package com.wensaas.core.impl.notification.template;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code sys_notification_template} Mapper。BaseMapper 走 MP TenantLine 自动注入；
 * 启动期 / SPI 同步路径需要跨 tenant_id 合并查询，单独写 xml 并由调用方 {@code InterceptorIgnoreHelper.handle(tenantLine=true)}。
 */
@Mapper
public interface SysNotificationTemplateMapper extends BaseMapper<SysNotificationTemplate> {

    /** 模板解析 / SPI 同步用：按 (tenant_id=0, templateCode) 命中业务方默认版本。 */
    SysNotificationTemplate selectPlatformDefault(@Param("templateCode") String templateCode);

    /** 列出某业务模块在 tenant_id=0 下的全部默认模板（SPI 比对用）。 */
    List<SysNotificationTemplate> selectPlatformAll();
}
