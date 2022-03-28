package com.zengliming.raft.actor;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;
import com.google.protobuf.GeneratedMessageV3;
import com.zengliming.raft.common.proto.CommonRoute;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author zengliming
 * @date 2022/3/26 23:52
 */
@Slf4j
public abstract class CommonActor extends AbstractBehavior<GeneratedMessageV3> {


    private CommonRoute currentRouter;

    private final String actorId;

    public CommonActor(String actorId, ActorContext<GeneratedMessageV3> context) {
        super(context);
        this.actorId = actorId;
    }

    @Override
    public Receive<GeneratedMessageV3> createReceive() {
        return newReceiveBuilder()
                .onMessage(CommonRoute.class, this::route)
                .onMessage(GeneratedMessageV3.class, (msg) -> route(new CommonRoute(null, null, null, msg, true)))
                .build();
    }

    private Behavior<GeneratedMessageV3> route(CommonRoute commonRoute) {
        final long start = System.currentTimeMillis();
        this.currentRouter = commonRoute;
        final GeneratedMessageV3 message = commonRoute.getMessage();
        try {
            processMessage(message);
        } catch (Exception e) {
            log.error("", e);
        }
        log.debug("process message cost {} ms", System.currentTimeMillis() - start);
        return this;
    }

    /**
     * 处理消息
     *
     * @param messageV3 需要处理的消息
     * @return 处理的结果
     * @throws Exception 异常
     */
    protected abstract boolean processMessage(GeneratedMessageV3 messageV3) throws Exception;

    public void reply(CommonRoute commonRoute, Supplier<GeneratedMessageV3> message) {
        if (Objects.nonNull(commonRoute) && Objects.nonNull(commonRoute.getReplayTo())) {
            commonRoute.getReplayTo().tell(message.get());
        }
    }

    public void reply(Supplier<GeneratedMessageV3> message) {
        this.reply(currentRouter, message);
    }

    public void preStop() {

    }
}
