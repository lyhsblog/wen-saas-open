package com.wensaas.core.impl.compliance.export;

import com.wensaas.core.api.compliance.spi.ExportObjectContributor;
import com.wensaas.core.api.compliance.spi.ExportObjectDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可导出对象注册表：聚合所有 {@link ExportObjectContributor} 声明的导出对象（含合规底座内置项）。
 *
 * <p>按贡献者注册顺序合并，{@code code} 去重（先注册者为准），供策略页呈现「是否需审批」开关、
 * 供发起导出时解析对象默认开关。声明为全局静态（不随租户变化），租户级覆盖落 {@code sys_export_approval_rule}。
 */
@Component
public class ExportObjectRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExportObjectRegistry.class);

    private final Map<String, ExportObjectDef> byCode = new LinkedHashMap<>();

    public ExportObjectRegistry(List<ExportObjectContributor> contributors) {
        for (ExportObjectContributor c : contributors) {
            for (ExportObjectDef def : c.exportObjects()) {
                if (def == null || def.code() == null || def.code().isBlank()) {
                    continue;
                }
                ExportObjectDef prev = byCode.putIfAbsent(def.code(), def);
                if (prev != null) {
                    log.warn("[compliance] 导出对象 code 重复，忽略后注册：code={} 已有={} 忽略={}",
                            def.code(), prev.name(), def.name());
                }
            }
        }
        log.info("[compliance] 已注册可导出对象 {} 个：{}", byCode.size(), byCode.keySet());
    }

    /** 全部声明（注册顺序）。 */
    public List<ExportObjectDef> all() {
        return List.copyOf(byCode.values());
    }

    /** 按 code 取声明，未注册返回 null。 */
    public ExportObjectDef find(String code) {
        return code == null ? null : byCode.get(code);
    }
}
