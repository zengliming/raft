package com.zengliming.raft.actor;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import com.google.protobuf.GeneratedMessageV3;
import com.zengliming.raft.context.NodeContext;
import com.zengliming.raft.proto.NodeEndpoint;
import com.zengliming.raft.proto.NodeId;
import com.zengliming.raft.proto.RpcCommand;
import com.zengliming.raft.rpc.RpcClient;
import com.zengliming.raft.rpc.RpcServer;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
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

    private final Map<NodeId, RpcClient> rpcClientMap;

    public RpcActor(ActorContext<GeneratedMessageV3> context) {
        super(getId(), context);
        this.rpcServer = new RpcServer();
        this.rpcServer.start(9999);
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
            final List<NodeEndpoint> nodeEndpoints = command.getTargetNodeEndpointsList();
            for (NodeEndpoint nodeEndpoint : nodeEndpoints) {
                try {
                    final RpcClient rpcClient = rpcClientMap.getOrDefault(nodeEndpoint.getId(), new RpcClient(nodeEndpoint));
                    rpcClient.request(command);
                    rpcClientMap.put(nodeEndpoint.getId(), rpcClient);
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
