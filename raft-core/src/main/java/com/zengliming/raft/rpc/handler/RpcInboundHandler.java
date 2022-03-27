package com.zengliming.raft.rpc.handler;

import com.google.protobuf.GeneratedMessageV3;
import com.zengliming.raft.actor.RaftActor;
import com.zengliming.raft.context.NodeContext;
import com.zengliming.raft.proto.AppendEntries;
import com.zengliming.raft.proto.RaftCommand;
import com.zengliming.raft.proto.RequestVoteResult;
import com.zengliming.raft.proto.RpcCommand;
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
            case REQUEST_VOTE: {
                log.debug("event is {}", rpcMessage.getRequestVote());
                final RaftCommand raftCommand = RaftCommand.newBuilder().setRequestVote(rpcMessage.getRequestVote()).build();
                final GeneratedMessageV3 response = NodeContext.ask(RaftActor.getId(), raftCommand, 2000L)
                        .toCompletableFuture().join();
                ctx.channel().writeAndFlush(RpcCommand.newBuilder().setRequestVoteResult(((RequestVoteResult) response)).build());
            }
            break;
            case APPEND_ENTRIES: {
                log.debug("event is {}", rpcMessage.getAppendEntries());
                final RaftCommand raftCommand = RaftCommand.newBuilder().setAppendEntries(AppendEntries.newBuilder().build()).build();
                NodeContext.publish(RaftActor.getId(), raftCommand);
            }
            break;
            case REQUEST_VOTE_RESULT:
            case APPEND_ENTRIES_RESULT:
            default:
        }
    }
}
