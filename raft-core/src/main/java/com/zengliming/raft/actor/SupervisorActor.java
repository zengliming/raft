package com.zengliming.raft.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import com.google.protobuf.GeneratedMessageV3;
import com.zengliming.raft.context.NodeContext;
import com.zengliming.raft.proto.RpcCommand;
import com.zengliming.raft.proto.actor.StartActor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * @author zengliming
 * @date 2022/3/27 1:10
 */
@Slf4j
public class SupervisorActor extends CommonActor {


    public SupervisorActor(ActorContext<GeneratedMessageV3> context) {
        super(getId(), context);
    }

    public static String getId() {
        return "supervisor";
    }

    public static Behavior<GeneratedMessageV3> create() {
        return Behaviors.setup(SupervisorActor::new);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected boolean processMessage(GeneratedMessageV3 messageV3) throws Exception {
        if (messageV3 instanceof StartActor) {
            StartActor startActor = (StartActor) messageV3;
            for (String actorClassName : startActor.getActorClassNameList()) {
                try {
                    final Class<?> clazz = Class.forName(actorClassName);
                    Behavior<GeneratedMessageV3> behavior = (Behavior<GeneratedMessageV3>) clazz.getMethod("create")
                            .invoke(null);
                    String actorId = (String) clazz.getMethod("getId").invoke(null);
                    final ActorRef<GeneratedMessageV3> actorRef = getContext().spawn(behavior, actorId, NodeContext.getPinnedDispatcher());
                    // NodeContext.ask(actorId, RpcCommand.newBuilder().build(), Duration.ofSeconds(10).toMillis()).toCompletableFuture().join();
                    NodeContext.getActorRefMap().put(actorId, actorRef);
                } catch (Exception e) {
                    log.error("start actor {} fail!", actorClassName, e);
                }
            }
        }
        return false;
    }
}
