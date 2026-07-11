package com.wensaas.core.impl.permission.org;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.cache.CacheRegion;
import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.impl.tenant.isolation.IgnoreStrategyScopes;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 租户部门集的持缓存读服务。
 *
 * <p>缓存区 {@code perm:dept-tree}，key = {@code tenantId}（每租户一条），value = 该租户全部部门的
 * {@code id → 瘦投影}（{@link DeptNode} 含 parent/name/ancestors/sort/status）。按 id 取 O(1)、组装树或遍历
 * 用 {@code values()}；子孙展开在内存里从 ancestors 推导，替代旧的 SQL {@code LIKE ancestors}。部门结构变更
 * （增删 / 移动 / 启停）只需 evict 本租户一个 key。
 *
 * <p>只投影展示 / 导航所需列、不缓存整行——审计 / 创建人 / 软删等列无展示用途，不入缓存。
 */
public class DeptService {

    /** 部门瘦投影：id→展示 / 导航所需列（ancestors 形如 {@code /0/1/3/}）。 */
    public record DeptNode(Long id, Long parentId, String name, String ancestors, Integer sortOrder, Integer status) {
    }

    private final SysDeptMapper deptMapper;
    private final CacheRegion<Map<Long, DeptNode>> region;

    public DeptService(SysDeptMapper deptMapper, CacheRegionFactory cacheRegionFactory, Duration ttl) {
        this.deptMapper = deptMapper;
        this.region = cacheRegionFactory.<Map<Long, DeptNode>>create("perm:dept-tree", ttl,
                JSON::toJSONString,
                s -> JSON.parseObject(s, new TypeReference<Map<Long, DeptNode>>() {}.getType()));
    }

    /**
     * 该租户全部部门的 {@code id → 瘦投影}（含停用、不含已删除；缓存读，miss 时整租户加载一次）。
     * 返回的是缓存值本体，调用方只读、不得修改。
     */
    public Map<Long, DeptNode> deptMap(long tenantId) {
        return region.getOrLoad(String.valueOf(tenantId), () -> load(tenantId));
    }

    /**
     * 给定直属部门 ID，返回「自身 + 全部子孙」部门 ID 集合（DEPT_AND_BELOW 用）。
     * 从缓存的部门集内存推导：子孙 = ancestors 含 {@code /deptId/} 的节点。
     */
    public Set<Long> expandSelfAndDescendants(long tenantId, Collection<Long> deptIds) {
        if (deptIds.isEmpty()) {
            return Set.of();
        }
        Map<Long, DeptNode> tree = deptMap(tenantId);
        Set<Long> result = new HashSet<>(deptIds);
        for (DeptNode node : tree.values()) {
            String ancestors = node.ancestors();
            if (ancestors == null) {
                continue;
            }
            for (Long deptId : deptIds) {
                if (ancestors.contains("/" + deptId + "/")) {
                    result.add(node.id());
                    break;
                }
            }
        }
        return result;
    }

    public void evict(long tenantId) {
        region.evict(String.valueOf(tenantId));
    }

    public void evictAll() {
        region.evictAll();
    }

    private Map<Long, DeptNode> load(long tenantId) {
        try (var ignored = IgnoreStrategyScopes.enter(IgnoreStrategyScopes.TENANT_LINE)) {
            return deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                            .eq(SysDept::getTenantId, tenantId)
                            .select(SysDept::getId, SysDept::getParentId, SysDept::getName,
                                    SysDept::getAncestors, SysDept::getSortOrder, SysDept::getStatus))
                    .stream().collect(Collectors.toMap(SysDept::getId,
                            d -> new DeptNode(d.getId(), d.getParentId(), d.getName(),
                                    d.getAncestors(), d.getSortOrder(), d.getStatus())));
        }
    }
}
