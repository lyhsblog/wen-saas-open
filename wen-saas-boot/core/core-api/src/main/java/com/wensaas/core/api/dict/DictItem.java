package com.wensaas.core.api.dict;

import java.util.List;
import java.util.Map;

/**
 * 字典项对外查询契约。
 *
 * <p>{@code children} 仅在层级分组的根列表中填充；非层级分组或叶子项为空列表。
 *
 * @param id         项 ID（雪花，JSON 序列化为字符串；admin 端用于 update / delete / status 路径参数）
 * @param code       项编码
 * @param name       显示名
 * @param parentCode 父项编码，null 表示顶级
 * @param sortOrder  同级排序权重
 * @param status     0=停用 / 1=启用（仅启用项会返回给业务方；停用快照保留逻辑由调用方处理）
 * @param remark     备注（运营维护信息；null 表示未设置）
 * @param source     来源
 * @param extra      扩展字段值
 * @param children   子项（层级分组用）
 */
public record DictItem(Long id,
                        String code,
                        String name,
                        String parentCode,
                        int sortOrder,
                        int status,
                        String remark,
                        DictSource source,
                        Map<String, Object> extra,
                        List<DictItem> children) {

    public DictItem {
        if (extra == null) {
            extra = Map.of();
        }
        if (children == null) {
            children = List.of();
        }
    }
}
