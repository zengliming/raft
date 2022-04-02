package com.zengliming.raft.rpc.handler;

import com.google.protobuf.GeneratedMessageV3;
import com.zengliming.raft.actor.RaftActor;
import com.zengliming.raft.context.RaftContext;
import com.zengliming.raft.proto.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zengliming
 * @date 2022/3/26 22:14
 */
@Slf4j
public class RpcInboundHandler extends SimpleChannelInboundHandler<RpcCommand> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcCommand rpcMessage) throws Exception {
        log.debug("rpc server receive message: {}", rpcMessage);
        switch (rpcMessage.getPayloadCase()) {
            case INIT:
                final Init init = rpcMessage.getInit();
                RaftContext.ask(RaftActor.getId(), init, 2000L)
                        .toCompletableFuture().join();
                ctx.channel().writeAndFlush(RpcCommand.newBuilder().build());
                break;
            case REQUEST_VOTE: {
                log.debug("event is {}", rpcMessage.getRequestVote());
                final RaftCommand raftCommand = RaftCommand.newBuilder().setRequestVote(rpcMessage.getRequestVote()).build();
                final GeneratedMessageV3 response = RaftContext.ask(RaftActor.getId(), raftCommand, 2000L)
                        .toCompletableFuture().join();
                final RequestVoteResult requestVoteResult = (RequestVoteResult) response;
                log.info("receive {} vote result {}", requestVoteResult.getMemberEndpoint().getId(), requestVoteResult.getVoteGranted());
                ctx.channel().writeAndFlush(RpcCommand.newBuilder().setRequestVoteResult(requestVoteResult).build());
            }
            break;
            case APPEND_ENTRIES: {
                final RaftCommand raftCommand = RaftCommand.newBuilder().setAppendEntries(AppendEntries.newBuilder().build()).build();
                RaftContext.publish(RaftActor.getId(), raftCommand);
            }
            break;
            case MEMBERSHIP_CHANGE: {
                log.debug("event is {}", rpcMessage.getMembershipChange());
                final RaftCommand raftCommand = RaftCommand.newBuilder().setMembershipChange(rpcMessage.getMembershipChange()).build();
                RaftContext.publish(RaftActor.getId(), raftCommand);
            }
            break;
            case REQUEST_VOTE_RESULT:
            case APPEND_ENTRIES_RESULT:
            default:
        }
    }
}
