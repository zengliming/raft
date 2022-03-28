package com.zengliming.raft.member.role;

import com.zengliming.raft.actor.RaftActor;
import com.zengliming.raft.context.RaftContext;
import com.zengliming.raft.proto.MemberId;
import com.zengliming.raft.proto.MemberRole;
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
public class FollowerMemberRole extends AbstractMemberRole {

    /**
     * 投票过的节点
     */
    @Getter
    @Setter
    private MemberId votedFor;

    /**
     * leader节点
     */
    @Getter
    private final MemberId leaderId;

    public FollowerMemberRole(Integer term) {
        this(term, null, null);
    }

    public FollowerMemberRole(Integer term, MemberId leaderId, MemberId votedFor) {
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
                RaftContext.publish(RaftActor.getId(), RaftCommand.newBuilder().setRoleChange(RoleChange.newBuilder().setTargetRole(MemberRole.CANDIDATE).build()).build());
            }
        });
    }
}
