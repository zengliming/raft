package com.zengliming.raft.actor;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import com.google.protobuf.GeneratedMessageV3;
import com.zengliming.raft.context.RaftContext;
import com.zengliming.raft.member.MemberManager;
import com.zengliming.raft.proto.*;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * @author zengliming
 * @date 2022/3/26 22:56
 */
@Slf4j
public class RaftActor extends CommonActor {

    private final MemberManager memberManager;


    public RaftActor(ActorContext<GeneratedMessageV3> context) {
        super(getId(), context);
        final MemberEndpoint memberEndpoint = MemberEndpoint.newBuilder()
                .setId(MemberId.newBuilder().setName(UUID.randomUUID().toString()).build())
                .setHost(RaftContext.getRaftConfig().getJoinHost())
                .setPort(RaftContext.getRaftConfig().getJoinPort())
                .build();
        this.memberManager = new MemberManager(memberEndpoint);
        try {
            final GeneratedMessageV3 members = RaftContext.ask(RpcActor.getId(), RpcCommand.newBuilder()
                    .addTargetMemberEndpoints(memberEndpoint)
                    .setRequestJoin(RequestJoin.newBuilder()
                            .setJoinEndpoint(memberEndpoint)
                            .build()).build(), 3000L).toCompletableFuture().join();
            this.memberManager.onSyncMembers(((SyncMembers) members).getMembersList());
        }catch (Exception e) {
            log.error("", e);
        }
        init();
    }

    private void init() {
        RaftContext.setMemberManager(this.memberManager);
        RaftContext.publish(getId(), RaftCommand.newBuilder()
                .setRoleChange(RoleChange.newBuilder()
                        .setTargetRole(MemberRole.FOLLOW)
                        .build())
                .build());
    }

    public static String getId() {
        return "raft";
    }

    public static Behavior<GeneratedMessageV3> create() {
        return Behaviors.setup(RaftActor::new);
    }

    @Override
    public boolean processMessage(GeneratedMessageV3 messageV3) {
        if (messageV3 instanceof RaftCommand) {
            RaftCommand raftCommand = (RaftCommand) messageV3;
            switch (raftCommand.getPayloadCase()) {
                case REQUEST_JOIN:
                    this.memberManager.join(raftCommand.getRequestJoin());
                    this.memberManager.getMemberMap().values();
                    reply(()->SyncMembers.newBuilder()
                            .addAllMembers(this.memberManager.getMemberMap().values())
                            .build());
                    break;
                case REQUEST_LEAVE:
                    this.memberManager.leave(raftCommand.getRequestLeave());
                    break;
                case REQUEST_VOTE:
                    final boolean voteResult = this.memberManager.handlerRequestVote(raftCommand.getRequestVote());
                    log.info("member {} reply vote result {} to {}.", RaftContext.getSelfId(), voteResult, raftCommand.getRequestVote().getMemberEndpoint().getId());
                    reply(() -> RequestVoteResult.newBuilder().setVoteGranted(voteResult).setTerm(raftCommand.getRequestVote().getTerm()).build());
                    break;
                case REQUEST_VOTE_RESULT:
                    final RequestVoteResult requestVoteResult = raftCommand.getRequestVoteResult();
                    log.info("request vote result is {}", requestVoteResult.getVoteGranted());
                    if (requestVoteResult.getVoteGranted()) {
                        this.memberManager.incrVote();
                    }
                    break;
                case SYNC_MEMBERS:
                    this.memberManager.onSyncMembers(raftCommand.getSyncMembers().getMembersList());
                    break;
                case ROLE_CHANGE:
                    this.memberManager.changeRole(raftCommand.getRoleChange());
                    break;
                case APPEND_ENTRIES:
                    this.memberManager.handlerOnAppendEntries(raftCommand.getAppendEntries());
                    break;
                case APPEND_ENTRIES_RESULT:
                    break;
                default:
            }
        }
        return false;
    }
}
