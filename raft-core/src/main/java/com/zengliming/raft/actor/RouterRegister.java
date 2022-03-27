package com.zengliming.raft.actor;

import com.zengliming.raft.common.proto.CommonProto;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * @author zengliming
 * @date 2022/3/27 0:06
 */
@EqualsAndHashCode(callSuper = false)
@Setter
@Getter
public class RouterRegister extends CommonProto {

    private String actorId;

    private String messageClassName;
}
