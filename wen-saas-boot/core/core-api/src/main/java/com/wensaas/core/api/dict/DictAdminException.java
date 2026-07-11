package com.wensaas.core.api.dict;

/**
 * 字典管理（写）业务异常。
 *
 * <p>读路径仍走 {@link DictService}，不抛业务异常。
 */
public class DictAdminException extends RuntimeException {

    public enum Code {
        /** 分组不存在 或 跨租户访问。404 */
        GROUP_NOT_FOUND,
        /** 分组编码冲突（同租户内）。409 */
        GROUP_CODE_DUPLICATE,
        /** 系统预置分组：禁止删除分组 / 改 schema / 改层级开关。400 */
        GROUP_SYSTEM_READONLY,
        /** 业务模块分组：禁止删除分组 / 改 schema。400 */
        GROUP_MODULE_READONLY,
        /** 分组下尚有项，不可删除。409 */
        GROUP_HAS_ITEMS,
        /** 项不存在 或 跨租户访问。404 */
        ITEM_NOT_FOUND,
        /** 项编码冲突（同分组内）。409 */
        ITEM_CODE_DUPLICATE,
        /** 系统预置 / 业务模块来源的项：禁止删除（仅可停用）。400 */
        ITEM_SYSTEM_READONLY,
        /** 项下有子项，不可删除。409 */
        ITEM_HAS_CHILDREN,
        /** 项扩展字段值未通过 schema 校验。400 */
        EXTRA_FIELD_VALIDATION_FAILED,
        /** 非层级分组传入了 parentId，或 parentId 跨分组。400 */
        ITEM_PARENT_INVALID,
        /** 层级深度超过 5 层。400 */
        ITEM_DEPTH_EXCEEDED
    }

    private final Code code;

    public DictAdminException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
