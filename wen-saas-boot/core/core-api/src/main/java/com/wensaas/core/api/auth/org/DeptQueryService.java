package com.wensaas.core.api.auth.org;

import java.util.List;
import java.util.Map;

/**
 * 部门信息 + 用户 ↔ 部门关系读服务：permission domain 对外暴露的部门成员关系查询入口。
 *
 * <p>跨 domain 读取部门（{@code sys_dept} / {@code sys_user_dept}）一律走本服务，
 * <b>不得直接注入对应 {@code *Mapper}</b>。
 *
 * <p>租户范围：显式带 {@code tenantId} 入参者按入参过滤；不带者（{@link #userIdsByDept} /
 * {@link #mainDeptIdOfUser} / {@link #deptById}）依赖调用方租户上下文，受 MP TenantLine 注入约束，
 * 本服务不另加旁路。这是有意为之的行为保持，不是遗漏。
 */
public interface DeptQueryService {

    /** 用户在指定租户下的部门绑定。 */
    List<DeptBinding> deptBindingsOfUser(long tenantId, long userId);

    /**
     * 用户的主部门 id（{@code is_main=1}）；无主部门返回 {@code null}。
     * 不带 tenantId，依赖调用方租户上下文 / 旁路（登录组装在 {@code @CrossTenantQuery} 内调用）。
     */
    Long mainDeptIdOfUser(long userId);

    /** 按 id 取部门信息；不存在返回 {@code null}。依赖调用方租户上下文。 */
    DeptInfo deptById(long deptId);

    /**
     * 租户全部部门的 {@code id → 信息}（含停用部门，不含已删除）。读整租户部门缓存，
     * 供部门名 / 层级路径批量拼装等展示场景 0 查库；返回 Map 调用方只读、不得修改。
     *
     * <p>缓存按入参 {@code tenantId} 分 key，适用于「调用方明确处于该租户业务上下文」的读取；
     * 跨租户按全局唯一 id 翻名的场景与按租户分 key 的缓存天然冲突，不走此入口，仍保持实时直查。
     */
    Map<Long, DeptInfo> deptInfosOfTenant(long tenantId);

    /**
     * 部门下的成员 userId；{@code includeSub=true} 时按 {@code ancestors} 匹配并入子部门成员。
     * 可能含重复，由调用方按需去重。依赖调用方租户上下文。
     */
    List<Long> userIdsByDept(long deptId, boolean includeSub);

    /** 用户-部门绑定：{@code main} 标识主部门。 */
    record DeptBinding(long deptId, boolean main) {}

    /** 部门信息：{@code ancestors} 形如 {@code /1/3/}，供拼层级路径。 */
    record DeptInfo(long id, String name, String ancestors) {}
}
