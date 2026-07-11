package com.wensaas.core.api.ops.setting;

/**
 * 系统参数分组（对应 {@code sys_setting.category}）。
 */
public enum SettingCategory {
    /** 平台运营信息（客服联系电话 / 邮箱 / 公告）。 */
    PLATFORM_INFO("platform_info"),
    /** 租户开通默认值（密码重置 TTL / 路由位修改频次）。 */
    TENANT_DEFAULT("tenant_default"),
    /** 安全策略（密码 / 登录锁定 / 会话 / 上传上限）。 */
    SECURITY_POLICY("security_policy"),
    /** 客户健康度评分参数（风险信号权重 + 分档阈值 + 判定参数）。 */
    HEALTH_SCORE("health_score");

    private final String dbValue;

    SettingCategory(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static SettingCategory fromDbValue(String value) {
        for (SettingCategory c : values()) {
            if (c.dbValue.equals(value)) return c;
        }
        throw new IllegalArgumentException("unknown setting category: " + value);
    }
}
