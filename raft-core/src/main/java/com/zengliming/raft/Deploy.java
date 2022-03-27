package com.zengliming.raft;

import akka.actor.ActorSystem;
import akka.actor.typed.ActorRef;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.javadsl.Adapter;
import com.google.protobuf.GeneratedMessageV3;
import com.zengliming.raft.actor.RaftActor;
import com.zengliming.raft.actor.RpcActor;
import com.zengliming.raft.actor.SupervisorActor;
import com.zengliming.raft.context.NodeContext;
import com.zengliming.raft.proto.actor.StartActor;

import java.time.Duration;
import java.util.Arrays;

public class Deploy {

    public static void main(String[] args) {
        DispatcherSelector pinnedDispatcher = DispatcherSelector.fromConfig("raft.pinned-dispatcher");
        final ActorSystem actorSystem = ActorSystem.create("raft");
        NodeContext.init(actorSystem, pinnedDispatcher);
        final ActorRef<GeneratedMessageV3> supervisorActorRef = Adapter.spawn(actorSystem, SupervisorActor.create(), SupervisorActor.getId(), pinnedDispatcher);
        NodeContext.getActorRefMap().put(SupervisorActor.getId(), supervisorActorRef);
        supervisorActorRef.tell(StartActor.newBuilder().addAllActorClassName(Arrays.asList(RaftActor.class.getCanonicalName(), RpcActor.class.getCanonicalName())).build());
    }
}
