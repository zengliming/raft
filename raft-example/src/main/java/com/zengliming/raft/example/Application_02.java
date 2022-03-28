package com.zengliming.raft.example;

import com.zengliming.raft.Deploy;
import com.zengliming.raft.config.RaftConfig;

/**
 * @author liming zeng
 * @create 2022-03-28 11:53
 */
public class Application_02 {

    public static void main(String[] args) {
        RaftConfig raftConfig = new RaftConfig();
        raftConfig.setPort(8888);
        raftConfig.setJoinHost("127.0.0.1");
        raftConfig.setJoinPort(9999);
        Deploy.deploy(raftConfig);
    }
}
