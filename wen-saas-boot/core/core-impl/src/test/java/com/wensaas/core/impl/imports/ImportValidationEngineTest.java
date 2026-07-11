package com.wensaas.core.impl.imports;

import com.wensaas.core.api.imports.ImportErrorCode;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.imports.NormalizedRow;
import com.wensaas.core.api.imports.PreviewRow;
import com.wensaas.core.api.imports.ValidationSink;
import com.wensaas.core.api.spi.def.FieldType;
import com.wensaas.core.api.spi.def.ImportEntityDef;
import com.wensaas.core.api.spi.def.ImportFieldDef;
import com.wensaas.core.api.spi.def.ReferenceDef;
import com.wensaas.core.api.spi.def.ReferenceMatch;
import com.wensaas.core.api.spi.def.UniqueScope;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ImportValidationEngineTest {

    private final ImportValidationEngine engine = new ImportValidationEngine();

    private static ImportEntityDef entity() {
        return ImportEntityDef.of("t.member", "成员", "t.task.import.member", List.of(
                new ImportFieldDef("姓名", "name", FieldType.STRING, true, null, UniqueScope.NONE, null, null),
                new ImportFieldDef("年龄", "age", FieldType.INTEGER, false, null, UniqueScope.NONE, null, null),
                new ImportFieldDef("手机", "phone", FieldType.STRING, false, "PHONE", UniqueScope.NONE, null, null),
                new ImportFieldDef("账号", "acct", FieldType.STRING, true, null, UniqueScope.IN_FILE, null, null)));
    }

    private static NormalizedRow row(int n, String name, String age, String phone, String acct) {
        return new NormalizedRow(n, Map.of("name", name, "age", age, "phone", phone, "acct", acct));
    }

    private static PreviewRow find(List<PreviewRow> rows, int rowNo) {
        return rows.stream().filter(r -> r.rowNo() == rowNo).findFirst().orElseThrow();
    }

    @Test
    void allValid_noErrors() {
        var rows = List.of(row(1, "张三", "20", "13800000000", "a1"));
        List<PreviewRow> out = engine.validate(entity(), rows, null).rows();
        assertThat(out).hasSize(1);
        assertThat(find(out, 1).ok()).isTrue();
    }

    @Test
    void requiredMissing_typeInvalid_formatInvalid_inFileDup() {
        var rows = List.of(
                row(1, "张三", "20", "13800000000", "a1"),
                row(2, "", "20", "13800000000", "a2"),          // 姓名必填缺失
                row(3, "李四", "abc", "13800000000", "a3"),      // 年龄非整数
                row(4, "王五", "20", "123", "a4"),               // 手机格式错
                row(5, "赵六", "20", "13800000000", "a1"));      // 账号文件内重复

        List<PreviewRow> out = engine.validate(entity(), rows, null).rows();

        assertThat(find(out, 1).ok()).isTrue();
        assertThat(find(out, 2).errors()).anyMatch(e -> e.code() == ImportErrorCode.REQUIRED_MISSING);
        assertThat(find(out, 3).errors()).anyMatch(e -> e.code() == ImportErrorCode.TYPE_INVALID);
        assertThat(find(out, 4).errors()).anyMatch(e -> e.code() == ImportErrorCode.FORMAT_INVALID);
        assertThat(find(out, 5).errors()).anyMatch(e -> e.code() == ImportErrorCode.DUPLICATE_IN_FILE);
    }

    @Test
    void resolver_errorsAreMerged() {
        var rows = List.of(row(1, "张三", "20", "13800000000", "a1"));
        List<PreviewRow> out = engine.validate(entity(), rows, new ImportValidationResolver() {
            @Override public String entityCode() { return "t.member"; }
            @Override public void validate(List<NormalizedRow> rs, ValidationSink sink) {
                sink.addError(1, "acct", ImportErrorCode.DUPLICATE_IN_DB, "账号已存在");
            }
        }).rows();
        assertThat(find(out, 1).ok()).isFalse();
        assertThat(find(out, 1).errors()).anyMatch(e -> e.code() == ImportErrorCode.DUPLICATE_IN_DB);
    }

    // ==================== 声明驱动 DB 校验 ====================

    /** 编号 BOTH：DB 命中回写 DUPLICATE_IN_DB（文案含表头），文件内重复叠加 DUPLICATE_IN_FILE。 */
    @Test
    void inDbDuplicate_flagged_andStacksWithInFile() {
        var def = ImportEntityDef.of("t.cust", "客户", "t.h", List.of(
                new ImportFieldDef("编号", "code", FieldType.STRING, false, null, UniqueScope.BOTH, null, null)));
        var rows = List.of(
                new NormalizedRow(1, Map.of("code", "C1")),
                new NormalizedRow(2, Map.of("code", "C1")),   // 文件内重复 + DB 已存在
                new NormalizedRow(3, Map.of("code", "C2")));
        var resolver = new ImportValidationResolver() {
            @Override public String entityCode() { return "t.cust"; }
            @Override public Set<String> existingDbValues(String fieldKey, Set<String> values) {
                assertThat(fieldKey).isEqualTo("code");
                assertThat(values).containsExactly("C1", "C2");
                return Set.of("C1");
            }
        };

        List<PreviewRow> out = engine.validate(def, rows, resolver).rows();

        assertThat(find(out, 1).errors()).anyMatch(e -> e.code() == ImportErrorCode.DUPLICATE_IN_DB
                && e.message().contains("编号已存在"));
        assertThat(find(out, 2).errors()).anyMatch(e -> e.code() == ImportErrorCode.DUPLICATE_IN_DB);
        assertThat(find(out, 2).errors()).anyMatch(e -> e.code() == ImportErrorCode.DUPLICATE_IN_FILE);
        assertThat(find(out, 3).ok()).isTrue();
    }

    /** 声明了 IN_DB 但取数口未实现（默认 null）→ 跳过不报错。 */
    @Test
    void inDbDeclared_lookupUnimplemented_skipped() {
        var def = ImportEntityDef.of("t.cust", "客户", "t.h", List.of(
                new ImportFieldDef("编号", "code", FieldType.STRING, false, null, UniqueScope.IN_DB, null, null)));
        var rows = List.of(new NormalizedRow(1, Map.of("code", "C1")));
        var resolver = new ImportValidationResolver() {
            @Override public String entityCode() { return "t.cust"; }
        };
        assertThat(engine.validate(def, rows, resolver).rows().getFirst().ok()).isTrue();
    }

    /** 声明了 DB 校验但无 resolver → 整段跳过。 */
    @Test
    void inDbDeclared_noResolver_skipped() {
        var def = ImportEntityDef.of("t.cust", "客户", "t.h", List.of(
                new ImportFieldDef("编号", "code", FieldType.STRING, false, null, UniqueScope.BOTH,
                        new ReferenceDef("t.x", ReferenceMatch.BY_CODE), null)));
        var rows = List.of(new NormalizedRow(1, Map.of("code", "C1")));
        assertThat(engine.validate(def, rows, null).rows().getFirst().ok()).isTrue();
    }

    /** 关联匹配：未命中报 REFERENCE_NOT_FOUND（文案用 label）；命中值 id 进 outcome。 */
    @Test
    void reference_missing_flagged_andIdsExposed() {
        var def = ImportEntityDef.of("t.m", "成员", "t.h", List.of(
                new ImportFieldDef("部门", "dept", FieldType.STRING, false, null, UniqueScope.NONE,
                        new ReferenceDef("t.dept", ReferenceMatch.BY_NAME, "部门", null), null)));
        var rows = List.of(
                new NormalizedRow(1, Map.of("dept", "销售部")),
                new NormalizedRow(2, Map.of("dept", "不存在部")));
        var resolver = new ImportValidationResolver() {
            @Override public String entityCode() { return "t.m"; }
            @Override public Map<String, Long> resolveReferences(ImportFieldDef field, Set<String> values) {
                assertThat(values).containsExactly("销售部", "不存在部");
                return Map.of("销售部", 7L);
            }
        };

        ImportValidationOutcome outcome = engine.validate(def, rows, resolver);

        assertThat(find(outcome.rows(), 1).ok()).isTrue();
        assertThat(find(outcome.rows(), 2).errors()).anyMatch(e -> e.code() == ImportErrorCode.REFERENCE_NOT_FOUND
                && e.message().contains("部门不存在"));
        assertThat(outcome.referenceIds("dept")).containsEntry("销售部", 7L);
        assertThat(outcome.referenceIds("other")).isEmpty();
    }

    /** label 为 null 时文案回退「关联对象」；映射允许 null value（containsKey 即存在）。 */
    @Test
    void reference_labelFallback_andNullIdValue() {
        var def = ImportEntityDef.of("t.d", "字典", "t.h", List.of(
                new ImportFieldDef("分组", "grp", FieldType.STRING, false, null, UniqueScope.NONE,
                        new ReferenceDef("t.grp", ReferenceMatch.BY_CODE), null)));
        var rows = List.of(
                new NormalizedRow(1, Map.of("grp", "g1")),
                new NormalizedRow(2, Map.of("grp", "g2")));
        var resolver = new ImportValidationResolver() {
            @Override public String entityCode() { return "t.d"; }
            @Override public Map<String, Long> resolveReferences(ImportFieldDef field, Set<String> values) {
                Map<String, Long> out = new HashMap<>();
                out.put("g1", null);   // 无数值 id 的目标
                return out;
            }
        };

        List<PreviewRow> out = engine.validate(def, rows, resolver).rows();

        assertThat(find(out, 1).ok()).isTrue();
        assertThat(find(out, 2).errors()).anyMatch(e -> e.code() == ImportErrorCode.REFERENCE_NOT_FOUND
                && e.message().contains("关联对象不存在"));
    }

    /** 前向声明（inFileKey）：更早行声明的值放行，自身行/更晚行声明的不放行。 */
    @Test
    void reference_inFileForwardDeclaration() {
        var def = ImportEntityDef.of("t.dept", "部门", "t.h", List.of(
                new ImportFieldDef("部门名", "name", FieldType.STRING, true, null, UniqueScope.IN_FILE, null, null),
                new ImportFieldDef("上级", "parent", FieldType.STRING, false, null, UniqueScope.NONE,
                        new ReferenceDef("t.dept", ReferenceMatch.BY_NAME, "上级部门", "name"), null)));
        var rows = List.of(
                new NormalizedRow(1, Map.of("name", "华东大区", "parent", "")),
                new NormalizedRow(2, Map.of("name", "销售部", "parent", "华东大区")),   // 更早行已声明 → 放行
                new NormalizedRow(3, Map.of("name", "自引用", "parent", "自引用")),     // 自身行 → 不放行
                new NormalizedRow(4, Map.of("name", "早于声明", "parent", "客服部")),   // 更晚行才声明 → 不放行
                new NormalizedRow(5, Map.of("name", "客服部", "parent", "DB部")));      // DB 已存在 → 放行
        var resolver = new ImportValidationResolver() {
            @Override public String entityCode() { return "t.dept"; }
            @Override public Map<String, Long> resolveReferences(ImportFieldDef field, Set<String> values) {
                return Map.of("DB部", 1L);
            }
        };

        List<PreviewRow> out = engine.validate(def, rows, resolver).rows();

        assertThat(find(out, 2).ok()).isTrue();
        assertThat(find(out, 3).errors()).anyMatch(e -> e.code() == ImportErrorCode.REFERENCE_NOT_FOUND);
        assertThat(find(out, 4).errors()).anyMatch(e -> e.code() == ImportErrorCode.REFERENCE_NOT_FOUND);
        assertThat(find(out, 5).ok()).isTrue();
    }

    /** 空值不进 DB 校验阶段（必填另判），取数口不被调用。 */
    @Test
    void emptyValues_skipDbPhase() {
        var def = ImportEntityDef.of("t.cust", "客户", "t.h", List.of(
                new ImportFieldDef("编号", "code", FieldType.STRING, false, null, UniqueScope.BOTH,
                        new ReferenceDef("t.x", ReferenceMatch.BY_CODE), null)));
        var rows = List.of(new NormalizedRow(1, Map.of("code", "")));
        var resolver = new ImportValidationResolver() {
            @Override public String entityCode() { return "t.cust"; }
            @Override public Set<String> existingDbValues(String fieldKey, Set<String> values) {
                throw new AssertionError("空值不应触发取数");
            }
            @Override public Map<String, Long> resolveReferences(ImportFieldDef field, Set<String> values) {
                throw new AssertionError("空值不应触发取数");
            }
        };
        assertThat(engine.validate(def, rows, resolver).rows().getFirst().ok()).isTrue();
    }
}
