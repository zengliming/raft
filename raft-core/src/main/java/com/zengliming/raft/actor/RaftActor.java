package com.zengliming.raft.actor;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import com.google.protobuf.GeneratedMessageV3;
import com.zengliming.raft.context.NodeContext;
import com.zengliming.raft.node.NodeManager;
import com.zengliming.raft.proto.*;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * @author zengliming
 * @date 2022/3/26 22:56
 */
@Slf4j
public class RaftActor extends CommonActor {

    private final NodeManager nodeManager;


    public RaftActor(ActorContext<GeneratedMessageV3> context) {
        super(getId(), context);
        final NodeEndpoint nodeEndpoint = NodeEndpoint.newBuilder()
                .setId(NodeId.newBuilder().setName(UUID.randomUUID().toString()).build())
                .setHost("127.0.0.1")
                .setPort(9999)
                .build();
        this.nodeManager = new NodeManager(nodeEndpoint);
        NodeContext.publish(RpcActor.getId(), RpcCommand.newBuilder().build());
        init();

    }

    private void init() {
        NodeContext.setNodeManager(this.nodeManager);
        NodeContext.publish(getId(), RaftCommand.newBuilder()
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
                    this.nodeManager.join(raftCommand.getRequestJoin());
                    break;
                case REQUEST_LEAVE:
                    this.nodeManager.leave(raftCommand.getRequestLeave());
                    break;
                case REQUEST_VOTE:
                    final boolean voteResult = this.nodeManager.handlerRequestVote(raftCommand.getRequestVote());
                    // todo 通知投票结果
                    log.info("通知投票结果 {}", voteResult);
                    reply(()-> RequestVoteResult.newBuilder().setVoteGranted(voteResult).setTerm(raftCommand.getRequestVote().getTerm()).build());
                    break;
                case REQUEST_VOTE_RESULT:
                    this.nodeManager.incrVote();
                    break;
                case SYNC_MEMBERS:
                    this.nodeManager.onSyncMembers(raftCommand.getSyncMembers().getMembersList());
                    break;
                case ROLE_CHANGE:
                    this.nodeManager.changeRole(raftCommand.getRoleChange());
                    break;
                case APPEND_ENTRIES:
                    break;
                case APPEND_ENTRIES_RESULT:
                    break;
                default:
            }
        }
        return false;
    }
}
