package com.wensaas.core.api.auth.org;

/**
 * 部门管理业务异常。
 */
public class DeptAdminException extends RuntimeException {

    public enum Code {
        /** 部门不存在 或 跨租户访问。404 */
        DEPT_NOT_FOUND,
        /** 同上级下部门名重复。409 */
        DEPT_NAME_DUPLICATE,
        /** 部门下有子部门，不可删除。409 */
        DEPT_HAS_CHILDREN,
        /** 部门下有成员（含兼任），不可删除。409 */
        DEPT_HAS_MEMBERS,
        /** parentId 不存在 / 跨租户 / 已软删。400 */
        DEPT_PARENT_INVALID,
        /** parentId 是自身或自身子孙，move 时产生循环。400 */
        DEPT_PARENT_IS_DESCENDANT,
        /** 层级深度超过 5 层。400 */
        DEPT_DEPTH_EXCEEDED,
        /** 公司根节点不可删 / 移 / 改名。400 */
        ROOT_DEPT_PROTECTED,
        /** 设为负责人的成员不属于该部门。400 */
        LEADER_NOT_MEMBER
    }

    private final Code code;

    public DeptAdminException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
