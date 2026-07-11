package com.wensaas.core.api.ops.health;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 客户健康度分档。
 *
 * <p>评分映射：{@code score >= healthyThreshold} → 健康；{@code >= warningThreshold} → 关注；否则 危险。
 * 阈值默认 80 / 50，可在运营「系统参数 / 健康度」运行时调整（{@code sys_setting.health_score}）。
 */
public enum HealthLevel {
    /** 健康（绿）：续约风险低，正常运营。 */
    HEALTHY("healthy"),
    /** 关注（黄）：有 1-2 个风险信号，需关注。 */
    WARNING("warning"),
    /** 危险（红）：多个风险信号，需立即处理。 */
    DANGER("danger");

    private final String dbValue;

    HealthLevel(String dbValue) {
        this.dbValue = dbValue;
    }

    /** 线格式：JSON 输出/输入统一用小写 dbValue（healthy/warning/danger），与前端契约 + overview 计数键一致。 */
    @JsonValue
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static HealthLevel fromDbValue(String value) {
        for (HealthLevel l : values()) {
            if (l.dbValue.equals(value)) {
                return l;
            }
        }
        throw new IllegalArgumentException("unknown health level: " + value);
    }

    /** 按阈值把评分归档。 */
    public static HealthLevel fromScore(int score, int healthyThreshold, int warningThreshold) {
        if (score >= healthyThreshold) {
            return HEALTHY;
        }
        if (score >= warningThreshold) {
            return WARNING;
        }
        return DANGER;
    }
}
