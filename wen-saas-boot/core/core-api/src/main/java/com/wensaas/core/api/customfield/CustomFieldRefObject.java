package com.wensaas.core.api.customfield;

/**
 * REF 类型自定义字段可关联的目标对象（字段管理「关联对象」下拉项）。
 *
 * <p>与 {@link CustomFieldObject}（宿主对象，指「谁能加自定义字段」）区分：本记录指「REF 字段能指向哪类
 * 数据记录」——= 内建数据实体（成员 / 部门）+ 已注册业务对象。<b>不含角色</b>（权限构件、非数据记录）、
 * 不含 platform-only 实体（租户 / 套餐）。
 *
 * @param code  实体类型 code（= {@code NameLookup.entityType()}，供 {@code EntityRefPicker} 反查）
 * @param label 中文显示名
 */
public record CustomFieldRefObject(String code, String label) {
}
