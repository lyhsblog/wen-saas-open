package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.dict.DictAdminService;
import com.wensaas.core.api.dict.DictGroup;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.spi.def.ImportFieldDef;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 字典导入取数口：命中的分组编码（供引擎按 {@code ReferenceDef} 声明批量匹配）。
 *
 * <p>分组无独立数值 id 语义，映射 value 置 null（引擎只看 containsKey）。
 * 项编码与已有数据的唯一冲突由执行期 {@code dictAdminService.createItem} 抛
 * {@code ITEM_CODE_DUPLICATE} 兜底（文件内重复已由引擎按 IN_FILE 拦截）。
 */
public class DictImportValidationResolver implements ImportValidationResolver {

    private final DictAdminService dictService;

    public DictImportValidationResolver(DictAdminService dictService) {
        this.dictService = dictService;
    }

    @Override
    public String entityCode() {
        return SysImportEntities.DICT_CODE;
    }

    @Override
    public Map<String, Long> resolveReferences(ImportFieldDef field, Set<String> values) {
        Set<String> groups = dictService.listGroups().stream()
                .map(DictGroup::code).collect(Collectors.toSet());
        // 允许 null value，不能用 Map.of / toMap
        Map<String, Long> out = new HashMap<>();
        for (String v : values) {
            if (groups.contains(v)) {
                out.put(v, null);
            }
        }
        return out;
    }
}
