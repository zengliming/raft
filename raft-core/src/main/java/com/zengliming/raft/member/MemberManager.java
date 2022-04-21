package com.zengliming.raft.member;

import com.google.common.collect.Lists;
import com.zengliming.raft.actor.RaftActor;
import com.zengliming.raft.actor.RpcActor;
import com.zengliming.raft.context.RaftContext;
import com.zengliming.raft.member.role.AbstractMemberRole;
import com.zengliming.raft.member.role.CandidateMemberRole;
import com.zengliming.raft.member.role.FollowerMemberRole;
import com.zengliming.raft.member.role.LeaderMemberRole;
import com.zengliming.raft.proto.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author zengliming
 * @date 2022/3/26 23:46
 */
@Slf4j
public class MemberManager {

    @Getter
    private MemberId selfId;

    @Getter
    private Member self;

    @Getter
    private Map<MemberId, Member> memberMap;

    @Setter
    private MemberEndpoint leaderEndpoint;

    private AbstractMemberRole memberRole;


    public MemberManager() {
        init();
    }

    private void init() {
        final MemberEndpoint shelfMemberEndpoint = MemberEndpoint.newBuilder()
                .setId(MemberId.newBuilder().setName(UUID.randomUUID().toString()).build())
                .setHost(RaftContext.getRaftConfig().getHost())
                .setPort(RaftContext.getRaftConfig().getPort())
                .build();
        this.selfId = shelfMemberEndpoint.getId();
        List<MemberEndpoint> memberEndpoints = new ArrayList<>();
        MemberEndpoint joinMemberEndpoint = null;
        if (Objects.nonNull(RaftContext.getRaftConfig().getJoinHost()) && Objects.nonNull(RaftContext.getRaftConfig().getJoinPort())) {
            joinMemberEndpoint = MemberEndpoint.newBuilder()
                    .setId(MemberId.newBuilder().setName(UUID.randomUUID().toString()).build())
                    .setHost(RaftContext.getRaftConfig().getJoinHost())
                    .setPort(RaftContext.getRaftConfig().getJoinPort())
                    .build();
            memberEndpoints.add(joinMemberEndpoint);
        }

        memberEndpoints.add(shelfMemberEndpoint);

        this.memberMap = this.buildMemberMap(memberEndpoints);
        this.self = this.memberMap.get(this.selfId);
        requestJoin(joinMemberEndpoint);
        RaftContext.publish(RaftActor.getId(), RaftCommand.newBuilder()
                .setRoleChange(RoleChange.newBuilder()
                        .setTargetRole(MemberRole.FOLLOW)
                        .build())
                .build());
    }

    private void requestJoin(MemberEndpoint memberEndpoint) {
        if (Objects.isNull(memberEndpoint)) {
            log.warn("not setting join info!");
            return;
        }
        try {
            RaftContext.ask(RpcActor.getId(), RpcCommand.newBuilder()
                    .addTargetMemberEndpoints(memberEndpoint)
                    .setMembershipChange(MembershipChange.newBuilder()
                            .setChangeMember(self)
                            .setMembershipChangeType(MembershipChangeType.JOIN)
                            .build())
                    .build(), 3000L).toCompletableFuture().join();
        } catch (Exception e) {
            log.error("cannot receive request join response: {}", e.getMessage());
        }
    }

    public void membershipChange(MembershipChange membershipChange, Consumer<Boolean> callback) {
        boolean success = false;
        switch (membershipChange.getMembershipChangeType()) {
            case LEAVE:
                this.leave(membershipChange.getChangeMember());
                success = true;
                break;
            case JOIN:
                this.join(membershipChange.getChangeMember());
                success = true;
                break;
            default:
                log.warn("unknown membership change type");
        }
        if (Objects.nonNull(callback)) {
            callback.accept(success);
        }
    }

    public void join(final Member member) {
        log.info("member {} join", member.getId());
        final Member selfMember = this.memberMap.get(selfId);
        final MemberEndpoint joinEndpoint = member.getMemberEndpoint();
        if (Objects.equals(selfMember.getRole(), MemberRole.LEADER)) {
            memberMap.put(joinEndpoint.getId(), transfer(joinEndpoint));
            RaftContext.publish(RpcActor.getId(), RpcCommand.newBuilder()
                            .addAllTargetMemberEndpoints(filter(Lists.newArrayList(selfId)))
                    .setMembershipChange(MembershipChange.newBuilder()
                            .setMembershipChangeType(MembershipChangeType.JOIN)
                            .setChangeMember(transfer(joinEndpoint))
                            .build())
                    .build());
        } else {
            if (Objects.nonNull(leaderEndpoint)) {
                RaftContext.publish(RpcActor.getId(), RpcCommand.newBuilder()
                        .addTargetMemberEndpoints(leaderEndpoint)
                        .setMembershipChange(MembershipChange.newBuilder()
                                .setMembershipChangeType(MembershipChangeType.JOIN)
                                .setChangeMember(transfer(joinEndpoint))
                                .build())
                        .build());
            }
        }
    }

    public void leave(Member member) {
        if (!this.memberMap.containsKey(member.getId())) {
            log.warn("membership leave but can not find member!");
            return;
        }
        if (Objects.equals(member.getRole(), MemberRole.LEADER)) {
            final MemberEndpoint leaveEndpoint = member.getMemberEndpoint();
            memberMap.remove(leaveEndpoint.getId());

        }
    }

    public void changeRole(RoleChange roleChange) {
        try {
            if (Objects.nonNull(memberRole)) {
                memberRole.cancelTask();
            }
        } catch (InterruptedException e) {
            log.info("cancel member current role task exception:", e);
        }
        switch (roleChange.getTargetRole()) {
            case FOLLOW: {
                log.info("member change to follow!");
                memberRole = new FollowerMemberRole(0, roleChange.getLeaderId(), null);
            }
            break;
            case CANDIDATE:
                memberRole = new CandidateMemberRole(0);
                this.leaderEndpoint = null;
                log.info("member change to candidate!");
                break;
            case LEADER:
                log.info("member change to leader! all member is {}", this.getMemberMap().keySet());
                memberRole = new LeaderMemberRole(0, () -> {
                }, null);
                break;
            default:
        }
        if (Objects.nonNull(memberRole)) {
            memberRole.startTask();
        }
    }

    public void incrVote() {
        log.info("incr vote counts!");
        if (memberRole instanceof CandidateMemberRole) {
            final CandidateMemberRole candidateMemberRole = (CandidateMemberRole) memberRole;
            candidateMemberRole.incVote();
            checkVote(candidateMemberRole);
        }
    }

    private void checkVote(CandidateMemberRole candidateMemberRole) {
        // 除去自己的投票
        if (candidateMemberRole.getVotes() > 1 && candidateMemberRole.getVotes() >= memberMap.size() / 2) {
            log.info("vote counts is {}, can becoming leader.", candidateMemberRole.getVotes());
            RaftContext.publish(RaftActor.getId(), RaftCommand.newBuilder()
                    .setRoleChange(RoleChange.newBuilder()
                            .setTargetRole(MemberRole.LEADER)
                            .build())
                    .build());
        }
    }

    public boolean handlerRequestVote(RequestVote requestVote) {
        if (this.memberRole.getTerm() > requestVote.getTerm()) {
            return false;
        }
        switch (memberRole.getMemberRole()) {
            case FOLLOW:
                final FollowerMemberRole followerMemberRole = (FollowerMemberRole) ((FollowerMemberRole) memberRole);
                if (Objects.nonNull(followerMemberRole.getVotedFor())) {
                    log.warn("current member voted for {}", followerMemberRole.getVotedFor());
                    return false;
                }
                followerMemberRole.setVotedFor(requestVote.getMemberEndpoint().getId());
                return true;
            case LEADER:
            case CANDIDATE:
            default:
                return false;
        }
    }

    public void handlerOnAppendEntries(AppendEntries appendEntries) {
        switch (memberRole.getMemberRole()) {
            case FOLLOW:
                memberRole.setLastTimestamp(System.currentTimeMillis());
                break;
            case CANDIDATE:
                this.leaderEndpoint = appendEntries.getLeader();
                RaftContext.publish(RaftActor.getId(), RaftCommand.newBuilder()
                        .setRoleChange(RoleChange.newBuilder()
                                .setTargetRole(MemberRole.FOLLOW)
                                .setLeaderId(appendEntries.getLeader().getId())
                                .build())
                        .build());
                break;
            case LEADER:
            default:
        }
    }

    private Map<MemberId, Member> buildMemberMap(Collection<MemberEndpoint> memberEndpoints) {
        Map<MemberId, Member> memberMap = new HashMap<>(memberEndpoints.size());
        memberEndpoints.forEach(memberEndpoint ->
                memberMap.put(memberEndpoint.getId(), transfer(memberEndpoint)));
        if (memberMap.isEmpty()) {
            throw new IllegalArgumentException("member is empty!");
        }
        return memberMap;
    }

    private Member transfer(MemberEndpoint memberEndpoint) {
        return Member.newBuilder()
                .setId(memberEndpoint.getId())
                .setMemberEndpoint(memberEndpoint)
                .build();
    }

    public Member findMember(MemberId id) {
        final Member member = this.memberMap.get(id);
        if (Objects.isNull(member)) {
            throw new IllegalArgumentException("member not exist");
        }
        return member;
    }

    public List<MemberEndpoint> filter(List<MemberId> ids) {
        if (Objects.isNull(ids) || ids.isEmpty()) {
            return memberMap.values().stream().map(Member::getMemberEndpoint).collect(Collectors.toList());
        }
        return memberMap.values().stream().filter(member -> !ids.contains(member.getId())).map(Member::getMemberEndpoint).collect(Collectors.toList());
    }

    public void onSyncMembers(List<Member> members) {
        this.memberMap.clear();
        log.info("receive members {}", members);
        for (Member member : members) {
            this.memberMap.put(member.getId(), member);
        }
    }
}
