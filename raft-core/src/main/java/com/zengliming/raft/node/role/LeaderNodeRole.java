package com.zengliming.raft.node.role;

import com.zengliming.raft.proto.MemberRole;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zengliming
 * @date 2022/3/26 20:50
 */
@Slf4j
@ToString
public class LeaderNodeRole extends AbstractNodeRole {


    public LeaderNodeRole(Integer term, Runnable timeout, Runnable logReplication) {
        super(MemberRole.LEADER, term);
        super.setRunnable(()-> {
            log.info("leader task");
        });
    }

}
