package com.wensaas.core.api.ops.role;

/**
 * 推荐角色模板管理业务异常。
 *
 * <p>Web 层映射：{@code RecommendedRoleAdminRestExceptionAdvice}。
 */
public class RecommendedRoleAdminException extends RuntimeException {

    public enum Code {
        /** 模板不存在（GET/PUT/DELETE/状态流转/复制源请求时）。 */
        TEMPLATE_NOT_FOUND,
        /** 创建/改名时名称已存在（运营内部唯一）。 */
        NAME_CONFLICT,
        /** 状态机不允许的迁移。 */
        INVALID_STATUS_TRANSITION,
        /** 已发布状态试图移除已包含的权限点（保护已启用客户）。 */
        PERMISSION_REMOVE_FORBIDDEN,
        /** 已发布状态试图移除已关联的套餐包（保护已启用客户）。 */
        PACKAGE_REMOVE_FORBIDDEN,
        /** 删除被拒：模板已被租户启用过，仅可下架。 */
        TEMPLATE_IN_USE,
        /** 包含的权限点编码未知或属于运营 ops.*（推荐角色仅含租户业务权限点）。 */
        INVALID_PERMISSION_CODE,
        /** 关联的套餐包编码不存在。 */
        PACKAGE_NOT_FOUND,
        /** 默认数据范围非法（仅 1=全部 / 2=本部门含下属 / 3=仅本人）。 */
        INVALID_DATA_SCOPE,
        /** 未关联任何套餐包（创建/发布要求 ≥ 1 个）。 */
        NO_PACKAGE_ASSOCIATED
    }

    private final Code code;

    public RecommendedRoleAdminException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
