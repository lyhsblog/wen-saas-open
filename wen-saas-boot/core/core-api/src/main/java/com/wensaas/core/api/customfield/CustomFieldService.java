package com.wensaas.core.api.customfield;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 自定义字段对外服务接口。
 *
 * <p>租户上下文：实现从 {@code TenantContext.require()} 取 {@code tenantId}。
 *
 * <p>v1 仅暴露"读 + 值校验"路径；CRUD（创建 / 改名 / 删 / 索引化切换）由内部
 * {@code CustomFieldDefManager} 提供，等 REST/UI 会话再开放。
 */
public interface CustomFieldService {

    /** 列出底座目前注册的所有业务对象（由 BusinessModuleDescriptor.customFieldObjects 声明）。 */
    List<CustomFieldObject> listObjects();

    /**
     * 列出 REF 字段可关联的目标对象 = 内建数据实体（成员 / 部门）+ 已注册业务对象。
     * 不含角色 / platform-only 实体。与 {@code CustomFieldDefManager} 创建期校验同一集合。
     */
    List<CustomFieldRefObject> listRefObjects();

    /** 列出当前租户在某业务对象下的所有未软删字段（按 displayOrder, id 升序）。 */
    List<CustomFieldDef> listFields(String objectCode);

    /** 取单个字段。 */
    Optional<CustomFieldDef> getField(String objectCode, String fieldCode);

    /** 取当前租户的字段 / 索引额度（不存在则按默认值惰性 init）。 */
    CustomFieldQuota getQuota();

    /**
     * 校验业务方提交的 {@code custom: {field_code: value}} 是否符合该对象的字段定义。
     *
     * <ul>
     *   <li>类型校验：与 {@link CustomFieldType} 匹配（含 ENUM 真查 dict）</li>
     *   <li>必填校验：所有 {@code required=true} 字段必须出现且非 null</li>
     *   <li>长度校验：TEXT ≤ 255 / LONG_TEXT ≤ 4000</li>
     *   <li>未声明字段：忽略（业务方传多余 key 不报错；v2 可加严格模式）</li>
     * </ul>
     *
     * @throws CustomFieldException code 见 {@link CustomFieldException.Code}
     */
    void validateValues(String objectCode, Map<String, Object> values);
}
