package com.zengliming.raft.actor;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import com.google.protobuf.GeneratedMessageV3;
import com.zengliming.raft.context.RaftContext;
import com.zengliming.raft.proto.MemberEndpoint;
import com.zengliming.raft.proto.MemberId;
import com.zengliming.raft.proto.RpcCommand;
import com.zengliming.raft.rpc.RpcClient;
import com.zengliming.raft.rpc.RpcServer;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author zengliming
 * @date 2022/3/26 23:37
 */
@Slf4j
public class RpcActor extends CommonActor {

    private final RpcServer rpcServer;

    private final Map<MemberId, RpcClient> rpcClientMap;

    public RpcActor(ActorContext<GeneratedMessageV3> context) {
        super(getId(), context);
        this.rpcServer = new RpcServer();
        this.rpcServer.start(RaftContext.getRaftConfig().getPort());
        this.rpcClientMap = new HashMap<>(16);
    }

    public static String getId() {
        return "rpc";
    }

    public static Behavior<GeneratedMessageV3> create() {
        return Behaviors.setup(RpcActor::new);
    }

    @Override
    public boolean processMessage(GeneratedMessageV3 messageV3) {
        if (messageV3 instanceof RpcCommand) {
            RpcCommand command = (RpcCommand) messageV3;
            final List<MemberEndpoint> memberEndpoints = command.getTargetMemberEndpointsList();
            for (MemberEndpoint memberEndpoint : memberEndpoints) {
                try {
                    final RpcClient rpcClient = rpcClientMap.getOrDefault(memberEndpoint.getId(), new RpcClient(memberEndpoint));
                    rpcClient.request(command);
                    rpcClientMap.put(memberEndpoint.getId(), rpcClient);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    @Override
    public void preStop() {
        if (Objects.nonNull(rpcServer)) {
            rpcServer.stop();
        }
        rpcClientMap.values().forEach(RpcClient::disconnect);
    }


}
