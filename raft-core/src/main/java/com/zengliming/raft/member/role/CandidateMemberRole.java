package com.zengliming.raft.member.role;

import com.google.common.collect.Lists;
import com.zengliming.raft.actor.RpcActor;
import com.zengliming.raft.context.RaftContext;
import com.zengliming.raft.proto.MemberRole;
import com.zengliming.raft.proto.RequestVote;
import com.zengliming.raft.proto.RpcCommand;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zengliming
 * @date 2022/3/26 21:10
 */
@Slf4j
@ToString
public class CandidateMemberRole extends AbstractMemberRole {

    /**
     * 拥有的票数
     */
    @Getter
    private Integer votes;

    public CandidateMemberRole(Integer term, Integer votes) {
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
    public CandidateMemberRole(Integer term) {
        this(term, 1);
    }

    private void requestVote() {
        log.info("send request vote!");
        RaftContext.publish(RpcActor.getId(), RpcCommand.newBuilder()
                .addAllTargetMemberEndpoints(RaftContext.getMemberManager().filter(Lists.newArrayList(RaftContext.getSelfId())))
                .setRequestVote(RequestVote.newBuilder()
                        .setMemberEndpoint(RaftContext.getMemberManager().findMember(RaftContext.getSelfId()).getMemberEndpoint())
                        .setLastLogIndex(super.getLastLogIndex())
                        .setLastLogTerm(super.getLastLogTerm())
                        .build())
                .build());
    }

    public void incVote() {
        this.votes++;
    }
}
