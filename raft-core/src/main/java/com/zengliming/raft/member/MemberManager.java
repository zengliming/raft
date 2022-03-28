package com.zengliming.raft.member;

import com.typesafe.config.Config;
import com.zengliming.raft.actor.RaftActor;
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
import java.util.stream.Collectors;

/**
 * @author zengliming
 * @date 2022/3/26 23:46
 */
@Slf4j
public class MemberManager {

    @Getter
    private final MemberId selfId;

    @Getter
    private Map<MemberId, Member> memberMap;

    @Setter
    private MemberEndpoint leaderEndpoint;

    private AbstractMemberRole memberRole;


    public MemberManager(MemberEndpoint memberEndpoint) {
        this(Collections.singletonList(memberEndpoint), memberEndpoint.getId());
    }

    public MemberManager(Collection<MemberEndpoint> memberEndpoint, MemberId selfId) {
        this.selfId = selfId;
        this.memberMap = this.buildMemberMap(memberEndpoint);
        final Config masterConfig = RaftContext.getConfig().getConfig("raft.master-member");
        this.leaderEndpoint = MemberEndpoint.newBuilder()
                .setHost(masterConfig.getString("host"))
                .setId(MemberId.newBuilder().setName(UUID.randomUUID().toString()).build())
                .setPort(masterConfig.getInt("port"))
                .build();
        memberMap.put(this.leaderEndpoint.getId(), this.transfer(this.leaderEndpoint));

    }


    public void join(RequestJoin requestJoin) {
        final Member member = this.memberMap.get(selfId);
        if (Objects.equals(member.getRole(), MemberRole.LEADER)) {
            final MemberEndpoint joinEndpoint = requestJoin.getJoinEndpoint();
            memberMap.put(joinEndpoint.getId(), transfer(joinEndpoint));
            syncMembers();
        }
    }

    public void leave(RequestLeave requestLeave) {
        final Member member = this.memberMap.get(selfId);
        if (Objects.equals(member.getRole(), MemberRole.LEADER)) {
            final MemberEndpoint leaveEndpoint = requestLeave.getLeaveEndpoint();
            memberMap.remove(leaveEndpoint.getId());
            syncMembers();

        }
    }

    public void syncMembers() {
        RaftContext.publish(RaftActor.getId(), RaftCommand.newBuilder()
                .setSyncMembers(SyncMembers.newBuilder()
                        .addAllMembers(memberMap.values())
                        .build())
                .build());
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
                log.info("member change to candidate!");
                break;
            case LEADER:
                log.info("member change to leader!");
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
                RaftContext.publish(RaftActor.getId(), RaftCommand.newBuilder()
                        .setRoleChange(RoleChange.newBuilder()
                                .setTargetRole(MemberRole.FOLLOW)
                                .setLeaderId(appendEntries.getMemberId())
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
