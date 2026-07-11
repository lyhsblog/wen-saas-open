package com.wensaas.core.impl.permission.org;

import com.wensaas.core.api.namesource.EntityTypes;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.org.DeptAdminException;
import com.wensaas.core.api.auth.org.DeptAdminService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.wensaas.core.impl.permission.eval.PermissionCacheEvictor;

/**
 * 部门管理（写）实现。
 *
 * <p>{@code ancestors} 物化路径形如 {@code /1/3/15/}；根部门为 {@code /}（不含自身）。
 * 子树批量重写在 {@link #move} 中通过 mapper 单条 UPDATE 完成。
 *
 * <p>部门结构变更（删 / 移动 / 启停）后调 {@link PermissionCacheEvictor#evictDeptTree}，按租户清部门树缓存
 * （每租户一个 key，不波及其它租户）。
 */
public class DeptAdminServiceImpl implements DeptAdminService {

    private static final int MAX_DEPTH = 5;
    private static final String ROOT_ANCESTOR = "/";

    private final SysDeptMapper deptMapper;
    private final SysUserDeptMapper userDeptMapper;
    private final SysDeptLeaderMapper deptLeaderMapper;
    private final SysUserMapper userMapper;
    private final PermissionCacheEvictor permissionCacheEvictor;

    public DeptAdminServiceImpl(SysDeptMapper deptMapper,
                                 SysUserDeptMapper userDeptMapper,
                                 SysDeptLeaderMapper deptLeaderMapper,
                                 SysUserMapper userMapper,
                                 PermissionCacheEvictor permissionCacheEvictor) {
        this.deptMapper = deptMapper;
        this.userDeptMapper = userDeptMapper;
        this.deptLeaderMapper = deptLeaderMapper;
        this.userMapper = userMapper;
        this.permissionCacheEvictor = permissionCacheEvictor;
    }

    @Override
    public List<DeptView> listTree() {
        long tenantId = TenantContext.require();
        List<SysDept> rows = deptMapper.listForTenant(tenantId);
        return buildTree(tenantId, rows);
    }

    @Override
    public DeptView getDetail(long deptId) {
        long tenantId = TenantContext.require();
        SysDept row = requireDept(tenantId, deptId);
        // 成员数与树节点同口径（countMembers），避免详情头部恒显 0 与左侧树不一致
        int members = deptMapper.countMembers(tenantId, deptId);
        return toView(row, members, List.of());
    }

    @Override
    @Transactional
    public DeptView create(CreateDeptRequest req) {
        requireNonBlank("name", req.name());
        long tenantId = TenantContext.require();
        long parentId = req.parentId() == null ? 0L : req.parentId();
        String parentAncestors = ROOT_ANCESTOR;
        if (parentId != 0L) {
            SysDept parent = requireDept(tenantId, parentId);
            int depth = depthOf(parent.getAncestors());
            if (depth + 1 >= MAX_DEPTH) {
                throw new DeptAdminException(DeptAdminException.Code.DEPT_DEPTH_EXCEEDED,
                        "层级深度超过 " + MAX_DEPTH);
            }
            parentAncestors = parent.getAncestors() + parent.getId() + "/";
        }
        ensureNameUnique(tenantId, parentId, req.name(), null);

        SysDept row = new SysDept();
        row.setTenantId(tenantId);
        row.setParentId(parentId);
        row.setName(req.name());
        row.setAncestors(parentAncestors);
        row.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        row.setStatus(1);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        deptMapper.insert(row);
        permissionCacheEvictor.evictDeptTree(tenantId);
        return toView(row, 0, List.of());
    }

    @Override
    @Transactional
    public DeptView update(long deptId, UpdateDeptRequest req) {
        long tenantId = TenantContext.require();
        SysDept row = requireDept(tenantId, deptId);
        // ② 审计对象名：加载后即登记「旧名/新名」（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_DEPT, String.valueOf(deptId), row.getName(), req.name());
        if (req.name() != null) {
            requireNonBlank("name", req.name());
            ensureNameUnique(tenantId, row.getParentId(), req.name(), deptId);
            row.setName(req.name());
        }
        if (req.sortOrder() != null) {
            row.setSortOrder(req.sortOrder());
        }
        row.setUpdatedAt(LocalDateTime.now());
        deptMapper.updateById(row);
        permissionCacheEvictor.evictDeptTree(tenantId);
        return toView(row, 0, List.of());
    }

    @Override
    @Transactional
    public void delete(long deptId) {
        long tenantId = TenantContext.require();
        SysDept row = requireDept(tenantId, deptId);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_DEPT, String.valueOf(deptId), row.getName());
        int kids = deptMapper.countChildren(tenantId, deptId);
        if (kids > 0) {
            throw new DeptAdminException(DeptAdminException.Code.DEPT_HAS_CHILDREN,
                    "部门下有 " + kids + " 个子部门");
        }
        int members = deptMapper.countMembers(tenantId, deptId);
        if (members > 0) {
            throw new DeptAdminException(DeptAdminException.Code.DEPT_HAS_MEMBERS,
                    "部门下有 " + members + " 个成员");
        }
        deptMapper.deleteById(deptId); // @TableLogic 软删
        permissionCacheEvictor.evictDeptTree(tenantId);
    }

    @Override
    @Transactional
    public DeptView move(long deptId, long newParentId) {
        long tenantId = TenantContext.require();
        SysDept self = requireDept(tenantId, deptId);
        if (newParentId == self.getParentId()) {
            return toView(self, 0, List.of());
        }
        String newParentAncestors;
        if (newParentId == 0L) {
            newParentAncestors = ROOT_ANCESTOR;
        } else {
            if (newParentId == deptId) {
                throw new DeptAdminException(DeptAdminException.Code.DEPT_PARENT_IS_DESCENDANT,
                        "不能把部门移到自身");
            }
            SysDept newParent = requireDept(tenantId, newParentId);
            // 防循环：newParent 的 ancestors 不能包含 self.id
            String selfMarker = "/" + deptId + "/";
            if (newParent.getAncestors().contains(selfMarker)) {
                throw new DeptAdminException(DeptAdminException.Code.DEPT_PARENT_IS_DESCENDANT,
                        "不能把部门移到自己的子孙下");
            }
            // 深度校验
            int newParentDepth = depthOf(newParent.getAncestors());
            int selfSubtreeDepth = maxSubtreeDepthRelative(tenantId, deptId, self.getAncestors());
            if (newParentDepth + 1 + selfSubtreeDepth >= MAX_DEPTH) {
                throw new DeptAdminException(DeptAdminException.Code.DEPT_DEPTH_EXCEEDED,
                        "移动后层级深度超过 " + MAX_DEPTH);
            }
            newParentAncestors = newParent.getAncestors() + newParent.getId() + "/";
        }
        ensureNameUnique(tenantId, newParentId, self.getName(), deptId);

        // 子树前缀重写：oldPrefix = self.ancestors + self.id + "/"
        String oldChildPrefix = self.getAncestors() + self.getId() + "/";
        String newChildPrefix = newParentAncestors + self.getId() + "/";

        // 1. 先更新自己
        self.setParentId(newParentId);
        self.setAncestors(newParentAncestors);
        self.setUpdatedAt(LocalDateTime.now());
        deptMapper.updateById(self);
        // 2. 再批量重写子孙 ancestors
        deptMapper.updateAncestorsPrefix(tenantId, oldChildPrefix, newChildPrefix);
        permissionCacheEvictor.evictDeptTree(tenantId);
        return toView(self, 0, List.of());
    }

    @Override
    @Transactional
    public void reorder(long parentId, List<Long> orderedIds) {
        long tenantId = TenantContext.require();
        if (orderedIds == null || orderedIds.isEmpty()) {
            return;
        }
        Set<Long> idSet = new HashSet<>(orderedIds);
        if (idSet.size() != orderedIds.size()) {
            throw new DeptAdminException(DeptAdminException.Code.DEPT_PARENT_INVALID,
                    "排序列表存在重复部门");
        }
        // 校验：orderedIds 必须恰好等于该父级下的全部直接子部门（防止漏传/串层级导致乱序）
        Map<Long, SysDept> byId = new HashMap<>();
        Set<Long> actualChildren = new HashSet<>();
        for (SysDept d : deptMapper.listForTenant(tenantId)) {
            byId.put(d.getId(), d);
            long p = d.getParentId() == null ? 0L : d.getParentId();
            if (p == parentId) {
                actualChildren.add(d.getId());
            }
        }
        if (!actualChildren.equals(idSet)) {
            throw new DeptAdminException(DeptAdminException.Code.DEPT_PARENT_INVALID,
                    "排序列表与该层级子部门不一致");
        }
        // 按下标整体重写 sort_order；仅更新发生变化的行
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < orderedIds.size(); i++) {
            SysDept row = byId.get(orderedIds.get(i));
            if (row.getSortOrder() != i) {
                row.setSortOrder(i);
                row.setUpdatedAt(now);
                deptMapper.updateById(row);
            }
        }
        permissionCacheEvictor.evictDeptTree(tenantId);
    }

    @Override
    @Transactional
    public DeptView setStatus(long deptId, boolean enabled) {
        long tenantId = TenantContext.require();
        SysDept row = requireDept(tenantId, deptId);
        row.setStatus(enabled ? 1 : 0);
        row.setUpdatedAt(LocalDateTime.now());
        deptMapper.updateById(row);
        permissionCacheEvictor.evictDeptTree(tenantId);
        return toView(row, 0, List.of());
    }

    @Override
    public List<DeptLeaderRef> listLeaders(long deptId) {
        long tenantId = TenantContext.require();
        requireDept(tenantId, deptId);
        List<Long> userIds = deptLeaderMapper.selectList(new LambdaQueryWrapper<SysDeptLeader>()
                        .eq(SysDeptLeader::getDeptId, deptId))
                .stream().map(SysDeptLeader::getUserId).toList();
        return toLeaderRefs(userIds);
    }

    @Override
    @Transactional
    public List<DeptLeaderRef> setLeaders(long deptId, List<Long> userIds) {
        long tenantId = TenantContext.require();
        requireDept(tenantId, deptId);
        // 全量替换：先删旧，再插新（每人须为该部门成员）
        deptLeaderMapper.delete(new LambdaQueryWrapper<SysDeptLeader>().eq(SysDeptLeader::getDeptId, deptId));
        List<Long> applied = new ArrayList<>();
        if (userIds != null) {
            Set<Long> members = new HashSet<>(deptUserIds(deptId));
            for (Long uid : new LinkedHashSet<>(userIds)) {
                if (uid == null) continue;
                if (!members.contains(uid)) {
                    throw new DeptAdminException(DeptAdminException.Code.LEADER_NOT_MEMBER,
                            "成员 " + uid + " 不属于该部门，不能设为负责人");
                }
                SysDeptLeader row = new SysDeptLeader();
                row.setTenantId(tenantId);
                row.setDeptId(deptId);
                row.setUserId(uid);
                row.setCreatedAt(LocalDateTime.now());
                deptLeaderMapper.insert(row);
                applied.add(uid);
            }
        }
        return toLeaderRefs(applied);
    }

    /** 该部门下的成员 user_id（含主 / 兼）。 */
    private List<Long> deptUserIds(long deptId) {
        return userDeptMapper.selectList(new LambdaQueryWrapper<SysUserDept>()
                        .eq(SysUserDept::getDeptId, deptId))
                .stream().map(SysUserDept::getUserId).toList();
    }

    private List<DeptLeaderRef> toLeaderRefs(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        Map<Long, SysUser> byId = new HashMap<>();
        for (SysUser u : userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getId, userIds))) {
            byId.put(u.getId(), u);
        }
        List<DeptLeaderRef> out = new ArrayList<>(userIds.size());
        for (Long uid : userIds) {
            SysUser u = byId.get(uid);
            if (u == null) continue;
            out.add(new DeptLeaderRef(u.getId(), u.getUsername(), u.getRealName()));
        }
        return out;
    }

    // ===================== helpers =====================

    private SysDept requireDept(long tenantId, long deptId) {
        SysDept row = deptMapper.selectById(deptId);
        if (row == null || row.getTenantId() != tenantId) {
            throw new DeptAdminException(DeptAdminException.Code.DEPT_NOT_FOUND,
                    "部门不存在: " + deptId);
        }
        return row;
    }

    private void ensureNameUnique(long tenantId, long parentId, String name, Long selfId) {
        int c = deptMapper.countByNameAndParent(tenantId, parentId, name, selfId);
        if (c > 0) {
            throw new DeptAdminException(DeptAdminException.Code.DEPT_NAME_DUPLICATE,
                    "同级下部门名重复: " + name);
        }
    }

    /** 部门子树相对深度（含自身为 1）。 */
    private int maxSubtreeDepthRelative(long tenantId, long deptId, String selfAncestors) {
        List<SysDept> all = deptMapper.listForTenant(tenantId);
        String selfMarker = selfAncestors + deptId + "/";
        int maxAbs = 0;
        for (SysDept r : all) {
            if (r.getId() == deptId || r.getAncestors().startsWith(selfMarker)) {
                int d = depthOf(r.getAncestors()) + 1; // 含自身
                maxAbs = Math.max(maxAbs, d);
            }
        }
        int selfDepth = depthOf(selfAncestors) + 1;
        return maxAbs - selfDepth; // 0 表示自身就是叶子
    }

    private static int depthOf(String ancestors) {
        // "/" → 0；"/1/" → 1；"/1/2/" → 2
        if (ancestors == null || ROOT_ANCESTOR.equals(ancestors)) return 0;
        int count = 0;
        for (int i = 0; i < ancestors.length(); i++) {
            if (ancestors.charAt(i) == '/') count++;
        }
        return count - 1; // 头尾各一个 / 不算
    }

    private List<DeptView> buildTree(long tenantId, List<SysDept> rows) {
        Map<Long, List<SysDept>> byParent = new HashMap<>();
        for (SysDept r : rows) {
            byParent.computeIfAbsent(r.getParentId() == null ? 0L : r.getParentId(),
                    k -> new ArrayList<>()).add(r);
        }
        return buildChildren(tenantId, 0L, byParent);
    }

    private List<DeptView> buildChildren(long tenantId, long parentId, Map<Long, List<SysDept>> byParent) {
        List<SysDept> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        List<DeptView> out = new ArrayList<>(children.size());
        for (SysDept c : children) {
            List<DeptView> sub = buildChildren(tenantId, c.getId(), byParent);
            int members = deptMapper.countMembers(tenantId, c.getId());
            out.add(toView(c, members, sub));
        }
        return out;
    }

    private static DeptView toView(SysDept r, int memberCount, List<DeptView> children) {
        return new DeptView(
                r.getId(),
                r.getParentId() == null ? 0L : r.getParentId(),
                r.getName(),
                r.getAncestors(),
                r.getSortOrder() == null ? 0 : r.getSortOrder(),
                r.getStatus() == null ? 1 : r.getStatus(),
                memberCount,
                children);
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
