package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.auth.org.DeptAdminService;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.spi.def.ImportFieldDef;

import java.util.Map;
import java.util.Set;

/**
 * 成员导入取数口：部门名 → id（供引擎按 {@code ReferenceDef} 声明批量匹配）。
 *
 * <p>账号 / 手机 / 邮箱与已有数据的唯一冲突（加密落库）由执行期
 * {@code memberAdminService.createMember} 抛 {@code USERNAME/EMAIL/MOBILE_CONFLICT} 兜底；
 * 文件内重复已由引擎按 IN_FILE 拦截。预览阶段暂不做 DB 账号查重（避免重造加密等值查）。
 */
public class MemberImportValidationResolver implements ImportValidationResolver {

    private final DeptAdminService deptService;

    public MemberImportValidationResolver(DeptAdminService deptService) {
        this.deptService = deptService;
    }

    @Override
    public String entityCode() {
        return SysImportEntities.MEMBER_CODE;
    }

    @Override
    public Map<String, Long> resolveReferences(ImportFieldDef field, Set<String> values) {
        return BuiltinImports.filterByKeys(BuiltinImports.deptNameToId(deptService), values);
    }
}
