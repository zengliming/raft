package com.zengliming.raft.node.role;

import com.google.common.collect.Lists;
import com.zengliming.raft.actor.RaftActor;
import com.zengliming.raft.actor.RpcActor;
import com.zengliming.raft.context.NodeContext;
import com.zengliming.raft.proto.*;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zengliming
 * @date 2022/3/26 21:10
 */
@Slf4j
@ToString
public class CandidateNodeRole extends AbstractNodeRole {

    /**
     * 拥有的票数
     */
    @Getter
    private Integer votes;

    public CandidateNodeRole(Integer term, Integer votes) {
        super(MemberRole.CANDIDATE, term);
        this.votes = votes;
        this.requestVote();
        super.setLastTimestamp(System.currentTimeMillis());
        super.setRunnable(() -> {
            if (System.currentTimeMillis() - super.getLastTimestamp() > super.getTimeout()) {
                log.info("request vote timeout, resend request!");
                this.votes = 1;
                this.requestVote();
                super.setLastTimestamp(System.currentTimeMillis());
            }
        });
    }

    /**
     * 转变成candidate时默认投自己一票
     *
     * @param term
     */
    public CandidateNodeRole(Integer term) {
        this(term, 1);
    }

    private void requestVote() {
        log.info("send request vote!");
        NodeContext.publish(RpcActor.getId(), RpcCommand.newBuilder()
                .addAllTargetNodeEndpoints(NodeContext.getNodeManager().filter(Lists.newArrayList(NodeContext.getSelfId())))
                .setRequestVote(RequestVote.newBuilder()
                        .setNodeEndpoint(NodeContext.getNodeManager().findMember(NodeContext.getSelfId()).getNodeEndpoint())
                        .setLastLogIndex(super.getLastLogIndex())
                        .setLastLogTerm(super.getLastLogTerm())
                        .build())
                .build());
    }

    public void incVote() {
        this.votes++;
    }
}
