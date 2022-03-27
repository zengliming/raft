package com.zengliming.raft.node.group;

import com.zengliming.raft.common.enums.ReplicatingState;
import com.zengliming.raft.proto.NodeEndpoint;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author zengliming
 * @date 2022/3/26 22:27
 */
@AllArgsConstructor
@Data
public class GroupMember {

    private final NodeEndpoint nodeEndpoint;

    private ReplicatingState replicatingState;


    public GroupMember(NodeEndpoint nodeEndpoint) {
        this(nodeEndpoint, null);
    }

}
