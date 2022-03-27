package com.zengliming.raft.rpc.process;

import com.zengliming.raft.proto.NodeId;
import com.zengliming.raft.rpc.RpcClient;
import com.zengliming.raft.rpc.RpcServer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zengliming
 * @date 2022/3/27 21:09
 */
public class RpcProcessor {

    private final RpcServer rpcServer;

    private final Map<NodeId, RpcClient> rpcClientMap;

    public RpcProcessor() {
        this.rpcServer = new RpcServer();
        this.rpcServer.start(9999);
        this.rpcClientMap = new HashMap<>(16);
    }
}
