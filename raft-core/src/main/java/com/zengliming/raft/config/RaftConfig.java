package com.zengliming.raft.config;

import lombok.Data;

/**
 * @author liming zeng
 * @create 2022-03-28 11:58
 */
@Data
public final class RaftConfig {

    private Integer port;

    private String joinHost;

    private Integer joinPort;

    private Long minTimeout;

    private Long maxTimeout;

    private Integer minMembers = 3;

}
