package com.wensaas.core.impl.auth.impersonate;

import java.util.Optional;

/**
 * 代登录 nonce 存储：签发 + 单次兑换。v1 实现 {@link RedisImpersonateNonceStore}。
 */
public interface ImpersonateNonceStore {

    /**
     * 生成 UUID nonce，持久化 data 到 Redis（TTL=5min）。
     *
     * @return 签发的 nonce（UUID 字符串）
     */
    String issue(ImpersonateNonceData data);

    /**
     * 原子 getAndDelete：取出并删除 nonce 对应的数据。
     * nonce 不存在（已用 / 已过期 / 未知）时返回 empty。
     */
    Optional<ImpersonateNonceData> redeem(String nonce);
}
