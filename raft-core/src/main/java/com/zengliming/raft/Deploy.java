package com.zengliming.raft;

import akka.actor.ActorSystem;
import akka.actor.typed.ActorRef;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.javadsl.Adapter;
import com.google.common.collect.Lists;
import com.google.protobuf.GeneratedMessageV3;
import com.zengliming.raft.actor.RaftActor;
import com.zengliming.raft.actor.RpcActor;
import com.zengliming.raft.actor.SupervisorActor;
import com.zengliming.raft.config.RaftConfig;
import com.zengliming.raft.context.RaftContext;
import com.zengliming.raft.proto.actor.ShutdownActor;
import com.zengliming.raft.proto.actor.StartActor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class Deploy {

    public static void deploy(RaftConfig raftConfig) {
        DispatcherSelector pinnedDispatcher = DispatcherSelector.fromConfig("raft.pinned-dispatcher");
        final ActorSystem actorSystem = ActorSystem.create("raft");
        RaftContext.init(actorSystem, pinnedDispatcher, raftConfig);
        final ActorRef<GeneratedMessageV3> supervisorActorRef = Adapter.spawn(actorSystem, SupervisorActor.create(), SupervisorActor.getId(), pinnedDispatcher);
        RaftContext.getActorRefMap().put(SupervisorActor.getId(), supervisorActorRef);
        supervisorActorRef.tell(StartActor.newBuilder().addAllActorClassName(Arrays.asList(RaftActor.class.getCanonicalName(), RpcActor.class.getCanonicalName())).build());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("shutdown!!!");
            RaftContext.ask(SupervisorActor.getId(), ShutdownActor.newBuilder()
                    .addAllActorIds(Lists.newArrayList(RaftActor.getId(), RpcActor.getId()))
                    .build(), 10000L).toCompletableFuture().join();
        }));
    }
}
