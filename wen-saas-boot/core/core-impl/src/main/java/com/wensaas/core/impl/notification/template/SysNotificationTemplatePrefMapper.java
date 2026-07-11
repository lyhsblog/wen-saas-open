package com.wensaas.core.impl.notification.template;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code sys_notification_template_pref} Mapper。
 *
 * <p>租户后台 CRUD 走 BaseMapper（MP TenantLine 自动按当前租户注入 / 过滤）；
 * 发送解析路径（{@link TemplateDataLoader}）已整段 {@code InterceptorIgnoreHelper.handle(tenantLine=true)}
 * 绕过拦截器，故用显式带 {@code tenant_id} 入参的 {@link #selectByTenantAndCode} / {@link #selectByTenant}。
 */
@Mapper
public interface SysNotificationTemplatePrefMapper extends BaseMapper<SysNotificationTemplatePref> {

    /** 发送解析用：显式按 (tenant_id, template_code) 命中租户偏好（绕拦截器场景）。 */
    SysNotificationTemplatePref selectByTenantAndCode(@Param("tenantId") long tenantId,
                                                      @Param("templateCode") String templateCode);

    /** 租户后台列表用：显式取某租户全部存活偏好（绕拦截器场景）。 */
    List<SysNotificationTemplatePref> selectByTenant(@Param("tenantId") long tenantId);
}
