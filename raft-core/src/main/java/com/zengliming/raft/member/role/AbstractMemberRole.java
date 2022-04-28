package com.zengliming.raft.member.role;

import com.zengliming.raft.proto.member.MemberRole;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * @author zengliming
 * @date 2022/3/26 20:09
 */
@ToString
public abstract class AbstractMemberRole {

    /**
     * 节点角色
     */
    @Getter
    private final MemberRole memberRole;

    /**
     * 任期
     */
    @Getter
    private final Integer term;

    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    private ScheduledFuture<?> scheduledFuture;

    /**
     * 超时时间
     */
    @Getter
    private Long timeout;

    /**
     * 上一个时间戳
     */
    @Getter
    @Setter
    private Long lastTimestamp;

    /**
     *
     */
    @Getter
    @Setter
    private Integer lastLogIndex;

    @Getter
    @Setter
    private Integer lastLogTerm;

    @Setter
    private Runnable runnable;

    protected AbstractMemberRole(MemberRole memberRole, Integer term) {
        this.memberRole = memberRole;
        this.term = term;
        this.scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1, r-> new Thread(r, "scheduler-" + memberRole));
        this.timeout = ThreadLocalRandom.current().nextLong(3000L, 4000L);
        this.lastTimestamp = 0L;
        this.lastLogIndex = 0;
        this.lastLogTerm = 0;
    }

    public void startTask() {
        if (Objects.nonNull(this.runnable)) {
            this.scheduledFuture = this.scheduledThreadPoolExecutor.scheduleAtFixedRate(this.runnable,0,1000L, TimeUnit.MILLISECONDS);
        }
    }

    public void cancelTask() throws InterruptedException {
        if (Objects.nonNull(scheduledFuture)) {
            scheduledFuture.cancel(false);
        }
    }

}
