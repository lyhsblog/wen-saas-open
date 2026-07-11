package com.wensaas.core.impl.permission.eval;

import com.wensaas.core.impl.permission.datascope.SysRoleDataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 权限定向查询 Mapper。按「用户 + 权限点」维度一条连表取数，取代旧的「按角色 fan-out
 * 逐项查 + 多层缓存聚合」。所有查询只连 sys_* 权限表、显式带 tenant_id（调用方用
 * {@code IgnoreStrategyScopes.TENANT_LINE} 绕过 MP 租户行注入）；这些表未注册数据权限，故不会被数据权限拦截器命中。
 *
 * <p>角色过滤口径统一：{@code sys_role.status = 1 AND deleted_at IS NULL}（启用且未软删）。
 */
@Mapper
public interface PermissionEvalMapper {

    /** 用户是否在「启用且未软删」的角色下被授予 codes 中任一权限点。codes 不可为空。 */
    boolean existsAnyGrant(@Param("tenantId") long tenantId,
                           @Param("userId") long userId,
                           @Param("codes") Collection<String> codes);

    /**
     * 「授予该 action 权限点」的各角色，连同其对该 action 的数据范围行：action 覆盖行 + 角色默认行（action IS NULL）
     * 经 LEFT JOIN 一并取出。每个授予角色返回 0~2 行；空结果集 = 用户未被授予该权限点。
     *
     * <p>仅 {@code roleId / action / dataScope} 三列有值（其余实体字段为 null）；授予但无任何范围配置的角色，
     * 其行 {@code action} 与 {@code dataScope} 均为 null（LEFT JOIN 落空）。grant-coupled：数据范围只在
     * 授予该权限点的角色内计算。
     */
    List<SysRoleDataScope> listGrantedRoleScopes(@Param("tenantId") long tenantId,
                                                 @Param("userId") long userId,
                                                 @Param("action") String action);

    /** 用户在「启用且未软删」角色下的全部权限点编码（并集、去重）。供 capabilities / ops 全量路径。 */
    List<String> listAllPermissionCodes(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /** 用户在「启用且未软删」角色下的全部默认数据范围 code（action IS NULL 行）。供全量路径取最宽兜底。 */
    List<Integer> listRoleDefaultScopeCodes(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /** 用户直属部门 ID（sys_user_dept，未展开子孙）。DEPT_ONLY 直接用；DEPT_AND_BELOW 交 DeptService 展开。 */
    List<Long> listOwnDeptIds(@Param("tenantId") long tenantId, @Param("userId") long userId);
}
