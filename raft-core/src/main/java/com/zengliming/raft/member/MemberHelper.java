package com.zengliming.raft.member;

import com.zengliming.raft.proto.MemberId;

import java.util.Objects;

/**
 * @author zengliming
 * @date 2022/3/26 23:14
 */
public class MemberHelper {

    /**
     * 节点的比较是比较节点里面的属性值是否相等
     *
     * @param o1 比较的对象
     * @param o2 比较的对象
     * @return 是否相等
     */
    public static boolean equals(MemberId o1, MemberId o2) {
        if (Objects.nonNull(o1) && Objects.nonNull(o2)) {
            return o1 == o2 || Objects.equals(o1.getName(), o2.getName());
        }
        return false;
    }
}
