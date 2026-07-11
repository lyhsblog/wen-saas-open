package com.wensaas.core.impl.dict.resolver;

import java.util.Optional;

/**
 * 字典分组解析器。{@link DictDataLoader} 提供"读 DB"语义，本接口在其上叠加缓存语义。
 *
 * <p>语义与 {@link com.wensaas.core.impl.permission.eval.PermissionResolver} 对齐。
 */
public interface DictResolver {

    /** 取分组快照；命中缓存直接返回，未命中先 load 再缓存。 */
    Optional<DictDataLoader.DictGroupSnapshot> resolve(long tenantId, String groupCode);

    /** 失效某租户某分组缓存。 */
    void evict(long tenantId, String groupCode);

    /** 失效所有字典缓存（模块下线 / 大规模 schema 变更时使用，SCAN 全表）。 */
    void evictAll();
}
