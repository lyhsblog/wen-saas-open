package com.wensaas.core.impl.permission.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.permission.UserRoleQueryService;

import java.util.List;
import java.util.Objects;

/**
 * {@link UserRoleQueryService} 实现。permission domain 内对外服务「用户-角色」成员关系查询，
 * 跨 domain 消费方经此入口而非裸查 {@code Sys*Mapper}。仅注本域 role 系 Mapper。
 */
public class UserRoleQueryServiceImpl implements UserRoleQueryService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;

    public UserRoleQueryServiceImpl(SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<RoleBrief> rolesOfUser(long tenantId, long userId) {
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, tenantId)
                .eq(SysUserRole::getUserId, userId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = links.stream().map(SysUserRole::getRoleId).toList();
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, tenantId)
                        .in(SysRole::getId, roleIds)).stream()
                .map(r -> new RoleBrief(r.getId(), r.getCode(), r.getName())).toList();
    }

    @Override
    public List<RoleBrief> rolesByIds(long tenantId, List<Long> roleIds) {
        List<Long> ids = roleIds == null ? List.of()
                : roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, tenantId)
                        .in(SysRole::getId, ids)).stream()
                .map(r -> new RoleBrief(r.getId(), r.getCode(), r.getName())).toList();
    }

    @Override
    public List<Long> userIdsByRole(long roleId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId)
                        .select(SysUserRole::getUserId)).stream()
                .map(SysUserRole::getUserId).toList();
    }
}
