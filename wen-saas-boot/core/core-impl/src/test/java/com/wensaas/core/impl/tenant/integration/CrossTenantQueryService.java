package com.wensaas.core.impl.tenant.integration;

import com.wensaas.core.api.tenant.CrossTenantQuery;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CrossTenantQueryService {

    private final TenantTestMapper mapper;

    public CrossTenantQueryService(TenantTestMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 跨租户全量统计（运营场景）。{@link CrossTenantQuery} 切面会在执行期间跳过 tenant_id 注入。
     */
    @CrossTenantQuery("平台监控：跨租户聚合")
    public int countAcrossAllTenants() {
        return mapper.count();
    }

    @CrossTenantQuery
    public List<Long> findAllIdsAcrossTenants() {
        return mapper.findAllIds();
    }
}
