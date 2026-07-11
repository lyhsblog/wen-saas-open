package com.wensaas.core.impl.compliance.export;

import com.wensaas.core.api.compliance.spi.ExportObjectContributor;
import com.wensaas.core.api.compliance.spi.ExportObjectDef;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 合规底座内置可导出对象。在尚无业务模块声明自己导出对象前，先提供一组
 * 横切底座层面的导出对象供策略页配置「是否需审批」开关。
 *
 * <p>含敏感个人信息的对象默认需审批（成员/操作日志），低敏对象默认放行（数据字典）；
 * 各对象的开关均可被主管理员按租户覆盖。业务模块上线后实现自己的
 * {@link ExportObjectContributor} 追加声明即可。
 */
@Component
public class BaseExportObjectContributor implements ExportObjectContributor {

    @Override
    public List<ExportObjectDef> exportObjects() {
        return List.of(
                new ExportObjectDef("member", "成员列表", "member", true),
                new ExportObjectDef("audit_log", "操作日志", "audit", true),
                new ExportObjectDef("dict", "数据字典", "system", false));
    }
}
