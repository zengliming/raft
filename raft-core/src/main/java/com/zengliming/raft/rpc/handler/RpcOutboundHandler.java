package com.zengliming.raft.rpc.handler;

import com.zengliming.raft.actor.RaftActor;
import com.zengliming.raft.context.RaftContext;
import com.zengliming.raft.proto.RaftCommand;
import com.zengliming.raft.proto.rpc.RpcCommand;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zengliming
 * @date 2022/3/27 1:41
 */
@Slf4j
public class RpcOutboundHandler extends SimpleChannelInboundHandler<RpcCommand> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcCommand rpcMessage) throws Exception {
        log.debug("rpc client receive message: {}", rpcMessage);
        switch (rpcMessage.getPayloadCase()) {
            case REQUEST_VOTE_RESULT:
                log.info("receive vote response!");
                RaftContext.publish(RaftActor.getId(), RaftCommand.newBuilder()
                                .setRequestVoteResult(rpcMessage.getRequestVoteResult())
                        .build());
                break;
            case APPEND_ENTRIES_RESULT: {
                log.debug("event is {}", rpcMessage.getRequestVoteResult());
            }
            break;
            case MEMBERSHIP_CHANGE:{
                RaftContext.ask(RaftActor.getId(), RaftCommand.newBuilder()
                        .setMembershipChange(rpcMessage.getMembershipChange())
                        .build(), 2000L);
            }break;
            case REQUEST_VOTE:
            case APPEND_ENTRIES:
            default:
        }
    }
}
