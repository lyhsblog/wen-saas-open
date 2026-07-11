package com.wensaas.core.api.ops.access;

/**
 * 运营域数据范围。
 *
 * <p>语义复用 {@code sys_role.data_scope} 字段值：
 * <ul>
 *   <li>{@code 1} → {@link #OPS_ALL}（看全部租户 / 数据）</li>
 *   <li>{@code 3} → {@link #OPS_OWNED}（仅看归属自己：{@code sys_tenant.csm_user_id=self}）</li>
 * </ul>
 *
 * <p>{@code 2} 在运营域不使用（无组织架构）。
 *
 * <p>多角色合并取最宽松：{@code OPS_ALL > OPS_OWNED}。
 */
public enum OpsDataScope {
    OPS_ALL(1),
    OPS_OWNED(3);

    private final int dbValue;

    OpsDataScope(int dbValue) {
        this.dbValue = dbValue;
    }

    public int dbValue() {
        return dbValue;
    }

    public static OpsDataScope fromDbValue(int value) {
        return switch (value) {
            case 1 -> OPS_ALL;
            case 3 -> OPS_OWNED;
            default -> throw new IllegalArgumentException(
                    "unsupported ops data_scope db value: " + value + " (运营域仅支持 1=ALL / 3=OWNED)");
        };
    }

    public OpsDataScope merge(OpsDataScope other) {
        if (this == OPS_ALL || other == OPS_ALL) {
            return OPS_ALL;
        }
        return OPS_OWNED;
    }
}
