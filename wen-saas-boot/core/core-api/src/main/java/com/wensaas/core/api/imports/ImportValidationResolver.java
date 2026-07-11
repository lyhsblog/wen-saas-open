package com.wensaas.core.api.imports;

import com.wensaas.core.api.spi.def.ImportFieldDef;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导入校验解析器：业务方实现，为引擎的声明驱动 DB 校验提供取数口，并承接声明覆盖不了的业务规则。
 *
 * <p>按 {@link #entityCode()} 注册（每实体一个）。引擎跑完必填/类型/格式/文件内唯一后：
 * ① 按字段声明（{@code UniqueScope.checksInDb()} / {@code ImportFieldDef.reference()}）批量回调
 * {@link #existingDbValues} / {@link #resolveReferences} 取数并回写
 * {@code DUPLICATE_IN_DB} / {@code REFERENCE_NOT_FOUND}；② 再整体回调 {@link #validate} 补业务规则错误。
 *
 * <p>调用发生在请求线程内，{@code TenantContext} / {@code UserContext} 已就绪，可直接查库。
 */
public interface ImportValidationResolver {

    /** 关联的导入实体编码（与 {@code ImportEntityDef.code} 一致）。 */
    String entityCode();

    /**
     * IN_DB/BOTH 查重取数口：返回 {@code values} 中已存在于 DB 的值（引擎据此回写 DUPLICATE_IN_DB）。
     * 实现可在查询前做等值变换（如确定性加密）。已实现取数口的实体，对非查重字段应返回空集。
     * 返回 {@code null} 表示未实现——引擎跳过该字段并 warn（声明了 checksInDb 却无配套取数）。
     */
    default Set<String> existingDbValues(String fieldKey, Set<String> values) {
        return null;
    }

    /**
     * 关联匹配取数口：值 → 目标 id。key 存在即目标存在；无数值 id 的目标（如字典分组按 code 关联）
     * value 置 {@code null}（引擎只看 containsKey，实现须用允许 null value 的 Map）。
     * 引擎把解析出的 id 映射随校验产物透传执行期 prepare，免二次查询。
     * 返回 {@code null} 表示未实现——引擎跳过该字段并 warn。
     */
    default Map<String, Long> resolveReferences(ImportFieldDef field, Set<String> values) {
        return null;
    }

    /**
     * 业务特有规则（引擎声明式校验覆盖不了的部分），命中失败用 {@code sink} 回写；默认无。
     */
    default void validate(List<NormalizedRow> rows, ValidationSink sink) {
    }
}
