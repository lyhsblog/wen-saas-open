package com.wensaas.core.api.notification.send;

import java.util.Objects;

/**
 * 收件人规格。
 *
 * @param type             类型
 * @param value            目标 ID（按 type 不同语义）：
 *                         MEMBER → Long userId；ROLE → Long roleId；DEPT / DEPT_LEADER → Long deptId
 * @param includeSubDepts  部门类型时是否递归含子部门；其他类型忽略
 */
public record RecipientSpec(RecipientType type, Long value, boolean includeSubDepts) {

    public RecipientSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }

    public static RecipientSpec member(long userId) {
        return new RecipientSpec(RecipientType.MEMBER, userId, false);
    }

    public static RecipientSpec role(long roleId) {
        return new RecipientSpec(RecipientType.ROLE, roleId, false);
    }

    public static RecipientSpec dept(long deptId, boolean includeSubDepts) {
        return new RecipientSpec(RecipientType.DEPT, deptId, includeSubDepts);
    }

    public static RecipientSpec deptLeader(long deptId) {
        return new RecipientSpec(RecipientType.DEPT_LEADER, deptId, false);
    }
}
