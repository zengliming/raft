package com.zengliming.raft.node;

import com.typesafe.config.Config;
import com.zengliming.raft.actor.RaftActor;
import com.zengliming.raft.context.NodeContext;
import com.zengliming.raft.node.role.AbstractNodeRole;
import com.zengliming.raft.node.role.CandidateNodeRole;
import com.zengliming.raft.node.role.FollowerNodeRole;
import com.zengliming.raft.node.role.LeaderNodeRole;
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
public class NodeManager {

    @Getter
    private final NodeId selfId;

    private Map<NodeId, Member> memberMap;

    @Setter
    private NodeEndpoint leaderEndpoint;

    private AbstractNodeRole nodeRole;


    public NodeManager(NodeEndpoint nodeEndpoint) {
        this(Collections.singletonList(nodeEndpoint), nodeEndpoint.getId());
    }

    public NodeManager(Collection<NodeEndpoint> nodeEndpoints, NodeId selfId) {
        this.selfId = selfId;
        this.memberMap = this.buildMemberMap(nodeEndpoints);
        final Config masterConfig = NodeContext.getConfig().getConfig("raft.master-node");
        this.leaderEndpoint = NodeEndpoint.newBuilder()
                .setHost(masterConfig.getString("host"))
                .setId(NodeId.newBuilder().setName(UUID.randomUUID().toString()).build())
                .setPort(masterConfig.getInt("port"))
                .build();
        memberMap.put(this.leaderEndpoint.getId(), this.transfer(this.leaderEndpoint));

    }


    public void join(RequestJoin requestJoin) {
        final Member member = this.memberMap.get(selfId);
        if (Objects.equals(member.getRole(), MemberRole.LEADER)) {
            final NodeEndpoint joinEndpoint = requestJoin.getJoinEndpoint();
            memberMap.put(joinEndpoint.getId(), transfer(joinEndpoint));
            syncMembers();
        }
    }

    public void leave(RequestLeave requestLeave) {
        final Member member = this.memberMap.get(selfId);
        if (Objects.equals(member.getRole(), MemberRole.LEADER)) {
            final NodeEndpoint leaveEndpoint = requestLeave.getLeaveEndpoint();
            memberMap.remove(leaveEndpoint.getId());
            syncMembers();

        }
    }

    public void syncMembers() {
        NodeContext.publish(RaftActor.getId(), RaftCommand.newBuilder()
                .setSyncMembers(SyncMembers.newBuilder()
                        .addAllMembers(memberMap.values())
                        .build())
                .build());
    }

    public void changeRole(RoleChange roleChange) {
        try {
            if (Objects.nonNull(nodeRole)) {
                nodeRole.cancelTask();
            }
        } catch (InterruptedException e) {
            log.info("取消之前节点任务失败", e);
        }
        switch (roleChange.getTargetRole()) {
            case FOLLOW: {
                log.info("member change to follow!");
                nodeRole = new FollowerNodeRole(0, roleChange.getLeaderId(), null);
            }
            break;
            case CANDIDATE:
                nodeRole = new CandidateNodeRole(0);
                log.info("member change to candidate!");
                checkVote((CandidateNodeRole) nodeRole);
                break;
            case LEADER:
                log.info("member change to leader!");
                nodeRole = new LeaderNodeRole(0, () -> {
                }, null);
                break;
            default:
        }
        if (Objects.nonNull(nodeRole)) {
            nodeRole.startTask();
        }
    }

    public void incrVote() {
        log.info("incr vote counts!");
        if (nodeRole instanceof CandidateNodeRole) {
            final CandidateNodeRole candidateNodeRole = (CandidateNodeRole) nodeRole;
            candidateNodeRole.incVote();
            checkVote(candidateNodeRole);
        }
    }

    private void checkVote(CandidateNodeRole candidateNodeRole) {
        if (candidateNodeRole.getVotes() >= memberMap.size() / 2) {
            log.info("vote counts is {}, can becoming leader.", candidateNodeRole.getVotes());
            NodeContext.publish(RaftActor.getId(), RaftCommand.newBuilder()
                    .setRoleChange(RoleChange.newBuilder()
                            .setTargetRole(MemberRole.LEADER)
                            .build())
                    .build());
        }
    }

    public boolean handlerRequestVote(RequestVote requestVote) {
        if (this.nodeRole.getTerm() > requestVote.getTerm()) {
            return false;
        }
        switch (nodeRole.getNodeRole()) {
            case FOLLOW:
                final FollowerNodeRole followerNodeRole = (FollowerNodeRole) ((FollowerNodeRole) nodeRole);
                if (Objects.nonNull(followerNodeRole.getVotedFor())) {
                    log.warn("current node voted for {}", followerNodeRole.getVotedFor());
                    return false;
                }
                followerNodeRole.setVotedFor(requestVote.getNodeEndpoint().getId());
                return true;
            case LEADER:
            case CANDIDATE:
            default:
                return false;
        }
    }

    private Map<NodeId, Member> buildMemberMap(Collection<NodeEndpoint> nodeEndpoints) {
        Map<NodeId, Member> memberMap = new HashMap<>(nodeEndpoints.size());
        nodeEndpoints.forEach(nodeEndpoint ->
                memberMap.put(nodeEndpoint.getId(), transfer(nodeEndpoint)));
        if (memberMap.isEmpty()) {
            throw new IllegalArgumentException("member is empty!");
        }
        return memberMap;
    }

    private Member transfer(NodeEndpoint nodeEndpoint) {
        return Member.newBuilder()
                .setId(nodeEndpoint.getId())
                .setNodeEndpoint(nodeEndpoint)
                .build();
    }

    public Member findMember(NodeId id) {
        final Member member = this.memberMap.get(id);
        if (Objects.isNull(member)) {
            throw new IllegalArgumentException("member not exist");
        }
        return member;
    }

    public List<NodeEndpoint> filter(List<NodeId> ids) {
        if (Objects.isNull(ids) || ids.isEmpty()) {
            return memberMap.values().stream().map(Member::getNodeEndpoint).collect(Collectors.toList());
        }
        return memberMap.values().stream().filter(node -> !ids.contains(node.getId())).map(Member::getNodeEndpoint).collect(Collectors.toList());
    }

    public void onSyncMembers(List<Member> members) {
        this.memberMap.clear();
        for (Member member : members) {
            this.memberMap.put(member.getId(), member);
        }
    }
}
