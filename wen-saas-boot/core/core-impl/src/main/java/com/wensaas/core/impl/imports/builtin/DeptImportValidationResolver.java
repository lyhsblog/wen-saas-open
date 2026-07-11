package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.auth.org.DeptAdminService;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.spi.def.ImportFieldDef;

import java.util.Map;
import java.util.Set;

/**
 * 部门导入取数口：上级部门名 → id（供引擎按 {@code ReferenceDef} 声明批量匹配）。
 *
 * <p>同文件"先建父再建子"（T12-02）由声明的 {@code inFileKey="name"} 交引擎统一判定；
 * 执行期 {@code DeptImportTaskHandler.writeRow} 同步回填新建部门的 name→id。
 * 部门名 DB 重名由执行期 {@code create} 兜底。
 */
public class DeptImportValidationResolver implements ImportValidationResolver {

    private final DeptAdminService deptService;

    public DeptImportValidationResolver(DeptAdminService deptService) {
        this.deptService = deptService;
    }

    @Override
    public String entityCode() {
        return SysImportEntities.DEPT_CODE;
    }

    @Override
    public Map<String, Long> resolveReferences(ImportFieldDef field, Set<String> values) {
        return BuiltinImports.filterByKeys(BuiltinImports.deptNameToId(deptService), values);
    }
}
