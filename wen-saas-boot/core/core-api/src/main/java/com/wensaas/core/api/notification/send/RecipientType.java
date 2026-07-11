package com.wensaas.core.api.notification.send;

/** 收件人规格类型（{@link RecipientSpec} 的 type 维度，发送时展开为具体成员）。 */
public enum RecipientType {
    /** 单个成员（value = userId）。 */
    MEMBER,
    /** 角色（value = roleId）。 */
    ROLE,
    /** 部门（value = deptId，配合 includeSubDepts 决定是否递归子部门）。 */
    DEPT,
    /** 部门负责人（value = deptId）；解析侧暂未实现展开（RecipientResolver 返回空 + warn）。 */
    DEPT_LEADER
}
