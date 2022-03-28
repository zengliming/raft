package com.zengliming.raft.rpc;

import com.zengliming.raft.context.RaftContext;
import com.zengliming.raft.proto.RpcCommand;
import com.zengliming.raft.rpc.handler.RpcInboundHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Future;

/**
 * @author zengliming
 * @date 2022/3/26 22:09
 */
@Slf4j
public class RpcServer {

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    private Future<String> future;

    public void start(int port) {

        this.future = RaftContext.getThreadPoolExecutor().submit(() -> {
            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                        // 指定连接队列大小
                        .option(ChannelOption.SO_BACKLOG, 128)
                        //KeepAlive
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        //Handler
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(@NonNull SocketChannel channel) throws Exception {
                                final ChannelPipeline pipeline = channel.pipeline();
                                //解码器，通过Google Protocol Buffers序列化框架动态的切割接收到的ByteBuf
                                pipeline.addLast(new ProtobufVarint32FrameDecoder());
                                pipeline.addLast(new ProtobufDecoder(RpcCommand.getDefaultInstance()));
                                //Google Protocol Buffers编码器
                                pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
                                //Google Protocol Buffers编码器
                                pipeline.addLast(new ProtobufEncoder());
                                pipeline.addLast(new RpcInboundHandler());
                            }
                        });
                ChannelFuture f = b.bind(port).sync();
                if (f.isSuccess()) {
                    log.info("Server,启动Netty服务端成功，端口号: {}", port);
                }
                // f.channel().closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "";
        });
    }

    public void stop() {
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
        this.future.cancel(true);
    }
}
