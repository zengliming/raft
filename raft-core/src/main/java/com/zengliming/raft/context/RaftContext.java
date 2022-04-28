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
import com.zengliming.raft.config.RaftConfig;
import com.zengliming.raft.member.MemberManager;
import com.zengliming.raft.proto.member.MemberId;
import lombok.Getter;

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
public class RaftContext {

    /**
     * 当前节点
     */
    @Getter
    private static MemberId selfId;

    /**
     * 成员列表
     */
    @Getter
    private static MemberManager memberManager;

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

    @Getter
    private static RaftConfig raftConfig;

    public static void init(ActorSystem system, DispatcherSelector pinnedDispatcher, RaftConfig raftConfig) {
        if (!init) {
            synchronized (RaftContext.class) {
                if (!init) {
                    init = true;
                    RaftContext.system = system;
                    scheduler = Adapter.toTyped(system.getScheduler());
                    config = system.settings().config();
                    RaftContext.pinnedDispatcher = pinnedDispatcher;
                    RaftContext.raftConfig = raftConfig;
                    threadPoolExecutor = new ThreadPoolExecutor(2, 8, 120, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1024), (r) -> new Thread(r));
                }
            }
        }
    }

    public static void setMemberManager(MemberManager memberManager) {
        RaftContext.memberManager = memberManager;
        selfId = memberManager.getSelfId();
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
