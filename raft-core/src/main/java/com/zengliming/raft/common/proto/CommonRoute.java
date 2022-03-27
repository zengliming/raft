package com.zengliming.raft.common.proto;

import akka.actor.typed.ActorRef;
import com.google.protobuf.GeneratedMessageV3;
import lombok.*;

/**
 * @author zengliming
 * @date 2022/3/26 23:57
 */
@Builder
@EqualsAndHashCode(callSuper = false)
@Setter
@Getter
@AllArgsConstructor
public class CommonRoute extends CommonProto {

    private String from;

    private String to;

    private ActorRef<GeneratedMessageV3> replayTo;

    private GeneratedMessageV3 message;

    private Boolean isSync;

}
