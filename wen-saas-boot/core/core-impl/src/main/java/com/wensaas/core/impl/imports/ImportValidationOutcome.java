package com.wensaas.core.impl.imports;

import com.wensaas.core.api.imports.PreviewRow;

import java.util.List;
import java.util.Map;

/**
 * 引擎校验产物：全量预览行 + 引擎解析出的关联 id 映射（fieldKey → 值→id），
 * 供执行期 prepare 复用（免与校验期重复查询）。
 *
 * <p>前向声明（{@code ReferenceDef.inFileKey}）命中的值不在映射内，
 * 由 handler 在 writeRow 建档后自行回填。
 *
 * @param rows         全量预览行（含错误标记）
 * @param referenceIds fieldKey → 值→id；无数值 id 的目标 value 为 null
 */
public record ImportValidationOutcome(List<PreviewRow> rows,
                                      Map<String, Map<String, Long>> referenceIds) {

    /** 某字段的 值→id 映射；未解析过返回空 Map。 */
    public Map<String, Long> referenceIds(String fieldKey) {
        return referenceIds.getOrDefault(fieldKey, Map.of());
    }
}
