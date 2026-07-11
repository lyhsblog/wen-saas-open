package com.wensaas.core.impl.permission.datascope;

/**
 * 数据范围：角色对某功能点可见数据行的范围（全部 / 本部门及以下 / 仅本部门 / 仅本人）。
 *
 * <p>DB 值映射（{@code sys_role_data_scope.data_scope}）：
 * 1=ALL / 2=DEPT_AND_BELOW / 3=SELF / 4=DEPT_ONLY。
 *
 * <p>多角色合并规则：取最宽松（{@link #widest}），宽松程度：ALL > DEPT_AND_BELOW > DEPT_ONLY > SELF。
 */
public enum DataScope {
    ALL(1),
    DEPT_AND_BELOW(2),
    DEPT_ONLY(4),
    SELF(3);

    private final int code;

    DataScope(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static DataScope fromCode(int code) {
        for (DataScope s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知 data_scope=" + code);
    }

    /** 取两个范围的"最宽松"（ALL > DEPT_AND_BELOW > DEPT_ONLY > SELF）。 */
    public static DataScope widest(DataScope a, DataScope b) {
        return order(a) >= order(b) ? a : b;
    }

    private static int order(DataScope s) {
        return switch (s) {
            case ALL -> 3;
            case DEPT_AND_BELOW -> 2;
            case DEPT_ONLY -> 1;
            case SELF -> 0;
        };
    }
}
