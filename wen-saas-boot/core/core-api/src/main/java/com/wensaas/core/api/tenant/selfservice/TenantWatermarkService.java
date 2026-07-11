package com.wensaas.core.api.tenant.selfservice;

import com.wensaas.core.api.tenant.TenantContext;
/**
 * 租户后台 · 安全合规 · 防截屏水印开关（租户级，默认开启）。
 *
 * <p>水印内容口径 = 真实姓名 + 用户名 + 实时时间，由前端平铺渲染（可溯源底纹）。本服务只负责
 * 租户级「是否开启」开关的读写；当前开启态同时经 {@code /api/v1/me/capabilities} 的
 * {@code screenshotWatermark} 字段下发给前端用于渲染门控。
 *
 * <p>运行在当前租户上下文内（{@code TenantContext#require()}）；运营域（tenant_id=0）无
 * sys_tenant 行，读不到即默认开启。
 */
public interface TenantWatermarkService {

    /** 当前租户防截屏水印是否开启（无偏好行 / NULL → 默认 true）。 */
    boolean isEnabled();

    /** 设置当前租户防截屏水印开关。 */
    void setEnabled(boolean enabled);
}
