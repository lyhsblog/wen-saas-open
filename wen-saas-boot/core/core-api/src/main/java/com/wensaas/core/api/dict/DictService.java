package com.wensaas.core.api.dict;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 字典服务对外接口。底座与业务模块统一从这里读取字典分组与字典项。
 *
 * <p>租户上下文：实现从 {@code TenantContext.require()} 取 {@code tenantId}，
 * 自动合并平台预置（tenant_id=0）+ 业务模块（tenant_id=0）+ 当前租户自定义（tenant_id&gt;0）。
 *
 * <p>v1 仅提供"读"路径；CRUD（追加项、停用项、改 schema）留给后续 UI 会话。
 */
public interface DictService {

    /** 取分组定义。 */
    Optional<DictGroup> getGroup(String groupCode);

    /**
     * 列出分组下所有启用项。
     * <p>层级分组返回根项 + 拼好的 {@code children} 子树；非层级分组返回扁平列表。
     */
    List<DictItem> listItems(String groupCode);

    /** 取单个字典项。 */
    Optional<DictItem> getItem(String groupCode, String itemCode);

    /**
     * 批量取字典项当前显示名，业务展示用（业务数据存编码，展示取最新名）。
     *
     * @return Map: itemCode → name；不存在的 code 不出现在返回值中
     */
    Map<String, String> batchGetItemNames(String groupCode, Collection<String> itemCodes);
}
