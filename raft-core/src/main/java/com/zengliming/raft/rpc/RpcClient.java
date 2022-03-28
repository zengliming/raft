package com.zengliming.raft.rpc;

import com.google.protobuf.GeneratedMessageV3;
import com.zengliming.raft.proto.MemberEndpoint;
import com.zengliming.raft.proto.RpcCommand;
import com.zengliming.raft.rpc.handler.RpcOutboundHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zengliming
 * @date 2022/3/27 1:36
 */
@Slf4j
public class RpcClient {

    private final MemberEndpoint memberEndpoint;
    private EventLoopGroup eventLoopGroup;

    private Channel channel;

    public RpcClient(MemberEndpoint memberEndpoint) throws Exception {
        this.memberEndpoint = memberEndpoint;
        connect();
    }

    private void connect() throws Exception {
        this.eventLoopGroup = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(this.eventLoopGroup).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(@NonNull SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        //解码器，通过Google Protocol Buffers序列化框架动态的切割接收到的ByteBuf
                        pipeline.addLast(new ProtobufVarint32FrameDecoder());
                        //将接收到的二进制文件解码成具体的实例，这边接收到的是服务端的ResponseBank对象实列
                        pipeline.addLast(new ProtobufDecoder(RpcCommand.getDefaultInstance()));
                        //Google Protocol Buffers编码器
                        pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
                        //Google Protocol Buffers编码器
                        pipeline.addLast(new ProtobufEncoder());

                        pipeline.addLast(new RpcOutboundHandler());
                    }
                });

        ChannelFuture channelFuture = bootstrap.connect(memberEndpoint.getHost(), memberEndpoint.getPort()).sync();
        this.channel = channelFuture.channel();
    }

    public void request(GeneratedMessageV3 messageV3) {
        log.debug("rpc msg {}", messageV3);
        this.channel.writeAndFlush(messageV3);
    }

    public void disconnect() {
        this.channel.closeFuture().syncUninterruptibly();
        this.eventLoopGroup.shutdownGracefully();
    }

}
