package com.wensaas.core.impl.openapi.ratelimit;

import com.wensaas.core.impl.openapi.gateway.AppKeyAuthenticationFilter;
/**
 * 开放 API 限流抽象。
 *
 * <p>支持两级：{@code apiKeyId=null} 走租户级窗口（限额取租户配置）；{@code apiKeyId!=null}
 * 走 {@code {tenantId, apiKeyId}} 凭证级窗口（限额由 {@code overrideLimit} 给定，即凭证
 * {@code rate_limit_qps}）。{@link AppKeyAuthenticationFilter} 在凭证设置了 qps 时两级叠加校验。
 */
public interface RateLimiter {

    /**
     * 尝试占一个名额（限额由限流器按维度自行查配置：apiKeyId=null 取租户级配置）。
     *
     * @param tenantId 租户；不可为 null（运营 / 匿名场景填 0）
     * @param apiKeyId 凭证 ID；null 走租户级聚合
     * @return 此次决策结果
     */
    default Decision tryAcquire(long tenantId, Long apiKeyId) {
        return tryAcquire(tenantId, apiKeyId, null);
    }

    /**
     * 尝试占一个名额，可显式指定本次窗口的限额。
     *
     * @param tenantId      租户；不可为 null
     * @param apiKeyId      凭证 ID；null 走租户级聚合窗口，非 null 走凭证级窗口
     * @param overrideLimit 本次窗口限额；null 时由限流器按租户配置取（凭证级限流应传凭证 {@code rate_limit_qps}）
     * @return 此次决策结果
     */
    Decision tryAcquire(long tenantId, Long apiKeyId, Integer overrideLimit);

    /** 限流决策。{@code limit}/{@code remaining}/{@code resetEpochSecond} 写入响应 header。 */
    record Decision(boolean allowed, int limit, int remaining, long resetEpochSecond) {
    }
}
