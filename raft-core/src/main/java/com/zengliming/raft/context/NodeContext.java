package com.zengliming.raft.context;

import akka.actor.ActorSystem;
import akka.actor.typed.ActorRef;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.AskPattern;
import com.google.protobuf.GeneratedMessageV3;
import com.typesafe.config.Config;
import com.zengliming.raft.common.proto.CommonRoute;
import com.zengliming.raft.node.NodeManager;
import com.zengliming.raft.proto.NodeId;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author zengliming
 * @date 2022/3/26 22:24
 */
public class NodeContext {

    /**
     * 当前节点
     */
    @Getter
    private static NodeId selfId;

    /**
     * 成员列表
     */
    @Getter
    private static NodeManager nodeManager;

    /**
     * 配置信息
     */
    @Getter
    private static Config config;


    @Getter
    private static DispatcherSelector pinnedDispatcher;

    @Getter
    private static ActorSystem system;

    @Getter
    private static Scheduler scheduler;

    @Getter
    private final static Map<String, ActorRef<GeneratedMessageV3>> actorRefMap = new HashMap<>(16);

    @Getter
    private static ThreadPoolExecutor threadPoolExecutor;

    private static volatile boolean init = false;


    public static void init(ActorSystem system, DispatcherSelector pinnedDispatcher) {
        if (!init) {
            synchronized (NodeContext.class) {
                if (!init) {
                    init = true;
                    NodeContext.system = system;
                    scheduler = Adapter.toTyped(system.getScheduler());
                    config = system.settings().config();
                    NodeContext.pinnedDispatcher = pinnedDispatcher;
                    threadPoolExecutor = new ThreadPoolExecutor(2,8,120, TimeUnit.SECONDS,new ArrayBlockingQueue<>(1024), (r) -> new Thread(r));
                }
            }
        }


    }

    public static void setNodeManager(NodeManager nodeManager) {
        NodeContext.nodeManager = nodeManager;
        selfId = nodeManager.getSelfId();
    }

    public static void publish(String to, GeneratedMessageV3 messageV3) {
        if (to != null) {
            actorRefMap.get(to).tell(messageV3);
        }
    }

    public static CompletionStage<GeneratedMessageV3> ask(String to, GeneratedMessageV3 messageV3, long timeoutMs) {
        final ActorRef<GeneratedMessageV3> actorRef = actorRefMap.get(to);
        return AskPattern.ask(actorRef, replyTo -> new CommonRoute(null, to, replyTo, messageV3, true), Duration.ofMillis(timeoutMs), scheduler);
    }

}
