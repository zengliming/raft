package com.zengliming.raft.member.role;

import com.zengliming.raft.actor.RpcActor;
import com.zengliming.raft.context.RaftContext;
import com.zengliming.raft.proto.AppendEntries;
import com.zengliming.raft.proto.Member;
import com.zengliming.raft.proto.MemberRole;
import com.zengliming.raft.proto.RpcCommand;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

/**
 * @author zengliming
 * @date 2022/3/26 20:50
 */
@Slf4j
@ToString
public class LeaderMemberRole extends AbstractMemberRole {


    public LeaderMemberRole(Integer term, Runnable timeout, Runnable logReplication) {
        super(MemberRole.LEADER, term);
        super.setRunnable(() -> {
            log.debug("leader task");
            RaftContext.publish(RpcActor.getId(), RpcCommand.newBuilder()
                    .addAllTargetMemberEndpoints(RaftContext.getMemberManager().getMemberMap().values().stream().map(Member::getMemberEndpoint).collect(Collectors.toList()))
                    .setAppendEntries(AppendEntries.newBuilder().build())
                    .build());
        });
    }

}
