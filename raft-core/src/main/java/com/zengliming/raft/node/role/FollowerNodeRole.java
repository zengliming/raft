package com.zengliming.raft.node.role;

import com.zengliming.raft.actor.RaftActor;
import com.zengliming.raft.context.NodeContext;
import com.zengliming.raft.proto.MemberRole;
import com.zengliming.raft.proto.NodeId;
import com.zengliming.raft.proto.RaftCommand;
import com.zengliming.raft.proto.RoleChange;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zengliming
 * @date 2022/3/26 20:50
 */
@Slf4j
@ToString
public class FollowerNodeRole extends AbstractNodeRole {

    /**
     * 投票过的节点
     */
    @Getter
    @Setter
    private NodeId votedFor;

    /**
     * leader节点
     */
    @Getter
    private final NodeId leaderId;

    public FollowerNodeRole(Integer term) {
        this(term, null, null);
    }

    public FollowerNodeRole(Integer term, NodeId leaderId, NodeId votedFor) {
        super(MemberRole.FOLLOW, term);
        this.leaderId = leaderId;
        this.votedFor = votedFor;
        super.setLastTimestamp(System.currentTimeMillis());
        super.setRunnable(() -> {
            if (System.currentTimeMillis() - super.getLastTimestamp() > super.getTimeout()) {
                log.info("follow time out, change role to candidate.");
                try {
                    super.cancelTask();
                    super.setRunnable(null);
                } catch (InterruptedException e) {
                    log.error("cancel follower task exception:", e);
                }
                NodeContext.publish(RaftActor.getId(), RaftCommand.newBuilder().setRoleChange(RoleChange.newBuilder().setTargetRole(MemberRole.CANDIDATE).build()).build());
            }
        });
    }
}
