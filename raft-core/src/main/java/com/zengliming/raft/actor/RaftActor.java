package com.zengliming.raft.actor;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import com.google.protobuf.GeneratedMessageV3;
import com.zengliming.raft.common.proto.CommonProto;
import com.zengliming.raft.context.RaftContext;
import com.zengliming.raft.member.MemberManager;
import com.zengliming.raft.proto.*;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zengliming
 * @date 2022/3/26 22:56
 */
@Slf4j
public class RaftActor extends CommonActor {

    private final MemberManager memberManager;


    public RaftActor(ActorContext<GeneratedMessageV3> context) {
        super(getId(), context);
        this.memberManager = new MemberManager();
        RaftContext.setMemberManager(this.memberManager);
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
                case INIT:
                    final Init init = raftCommand.getInit();
                    this.memberManager.changeRole(RoleChange.newBuilder()
                            .setLeaderId(init.getLeader().getId())
                            .setTargetRole(MemberRole.FOLLOW)
                            .build());
                    this.memberManager.onSyncMembers(init.getMembersList());
                    reply(() -> new CommonProto());
                case MEMBERSHIP_CHANGE:
                    final MembershipChange membershipChange = raftCommand.getMembershipChange();
                    this.memberManager.membershipChange(membershipChange, (success) -> {
                    });
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
