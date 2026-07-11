package com.wensaas.core.impl.permission.org;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.org.DeptQueryService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link DeptQueryService} 实现。permission domain 内对外服务部门信息 / 「用户-部门」成员关系查询，
 * 跨 domain 消费方经此入口而非裸查 {@code Sys*Mapper}。仅注本域 dept 系 Mapper 与域内缓存读服务
 * {@link DeptService}（后者不直接对外暴露，跨域读一律经本 facade）。
 */
public class DeptQueryServiceImpl implements DeptQueryService {

    private final SysUserDeptMapper userDeptMapper;
    private final SysDeptMapper deptMapper;
    private final DeptService deptService;

    public DeptQueryServiceImpl(SysUserDeptMapper userDeptMapper, SysDeptMapper deptMapper,
                                DeptService deptService) {
        this.userDeptMapper = userDeptMapper;
        this.deptMapper = deptMapper;
        this.deptService = deptService;
    }

    @Override
    public List<DeptBinding> deptBindingsOfUser(long tenantId, long userId) {
        return userDeptMapper.selectList(new LambdaQueryWrapper<SysUserDept>()
                        .eq(SysUserDept::getTenantId, tenantId)
                        .eq(SysUserDept::getUserId, userId)).stream()
                .map(b -> new DeptBinding(b.getDeptId(), b.getIsMain() != null && b.getIsMain() == 1))
                .toList();
    }

    @Override
    public Long mainDeptIdOfUser(long userId) {
        SysUserDept ud = userDeptMapper.selectOne(new LambdaQueryWrapper<SysUserDept>()
                .eq(SysUserDept::getUserId, userId)
                .eq(SysUserDept::getIsMain, 1)
                .last("LIMIT 1"));
        return ud == null ? null : ud.getDeptId();
    }

    @Override
    public DeptInfo deptById(long deptId) {
        SysDept d = deptMapper.selectById(deptId);
        return d == null ? null : new DeptInfo(d.getId(), d.getName(), d.getAncestors());
    }

    @Override
    public Map<Long, DeptInfo> deptInfosOfTenant(long tenantId) {
        // 整租户部门树缓存的瘦投影 → DeptInfo，0 查库（缓存 miss 时由 DeptService 整租户加载一次）。
        return deptService.deptMap(tenantId).values().stream()
                .collect(Collectors.toMap(DeptService.DeptNode::id,
                        n -> new DeptInfo(n.id(), n.name(), n.ancestors())));
    }

    @Override
    public List<Long> userIdsByDept(long deptId, boolean includeSub) {
        List<Long> deptIds = new ArrayList<>();
        deptIds.add(deptId);
        if (includeSub) {
            // 子部门：ancestors LIKE '%/{deptId}/%'（权限引擎 v1 既有口径）
            deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                            .like(SysDept::getAncestors, "/" + deptId + "/")
                            .select(SysDept::getId))
                    .forEach(d -> deptIds.add(d.getId()));
        }
        return userDeptMapper.selectList(new LambdaQueryWrapper<SysUserDept>()
                        .in(SysUserDept::getDeptId, deptIds)
                        .select(SysUserDept::getUserId)).stream()
                .map(SysUserDept::getUserId).toList();
    }
}
