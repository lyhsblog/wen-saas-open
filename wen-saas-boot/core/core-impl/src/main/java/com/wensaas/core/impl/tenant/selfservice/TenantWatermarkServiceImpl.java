package com.wensaas.core.impl.tenant.selfservice;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.tenant.selfservice.TenantWatermarkService;
import org.springframework.transaction.annotation.Transactional;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;

/**
 * 防截屏水印租户级开关实现。读写 {@code sys_tenant.screenshot_watermark_enabled}（NULL=默认开启）。
 *
 * <p>{@code sys_tenant} 为平台白名单表，更新不经 MP TenantLine；本服务只读写
 * {@code TenantContext#require()} 取到的当前租户行，安全。范式同 {@link TenantCompanyServiceImpl}。
 */
public class TenantWatermarkServiceImpl implements TenantWatermarkService {

    private final SysTenantMapper tenantMapper;

    public TenantWatermarkServiceImpl(SysTenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    @Override
    public boolean isEnabled() {
        long tenantId = TenantContext.require();
        SysTenant tenant = tenantMapper.selectById(tenantId);
        return tenant == null
                || tenant.getScreenshotWatermarkEnabled() == null
                || tenant.getScreenshotWatermarkEnabled();
    }

    @Override
    @Transactional
    public void setEnabled(boolean enabled) {
        long tenantId = TenantContext.require();
        tenantMapper.update(null, new LambdaUpdateWrapper<SysTenant>()
                .eq(SysTenant::getId, tenantId)
                .set(SysTenant::getScreenshotWatermarkEnabled, enabled)
                .set(SysTenant::getUpdatedBy, UserContext.require().userId()));
    }
}
