package com.wensaas.core.impl.spi.module;

import com.wensaas.core.api.spi.BusinessModuleConflictException;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 按 {@link com.wensaas.core.api.spi.ModuleMetadata#dependencies()} 做 Kahn 入度法拓扑排序。
 *
 * <p>同入度按模块编码字典序出队，确保启动日志顺序稳定。
 * 自依赖归为循环依赖；依赖未在 enabled 列表（含 disabled）视为"依赖未注册"。
 * 所有错误一次性收集后统一抛 {@link BusinessModuleConflictException}。
 */
@Component
public class DependencyTopologySorter {

    public List<BusinessModuleDescriptor> sort(List<BusinessModuleDescriptor> modules) {
        if (modules.isEmpty()) {
            return List.of();
        }

        Map<String, BusinessModuleDescriptor> byCode = new LinkedHashMap<>();
        for (BusinessModuleDescriptor m : modules) {
            byCode.put(m.metadata().code(), m);
        }

        List<String> errors = new ArrayList<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Set<String>> dependents = new HashMap<>();
        for (String code : byCode.keySet()) {
            indegree.put(code, 0);
            dependents.put(code, new HashSet<>());
        }

        for (BusinessModuleDescriptor m : modules) {
            String code = m.metadata().code();
            for (String dep : m.metadata().dependencies()) {
                if (dep.equals(code)) {
                    errors.add("模块循环依赖: " + code + " -> " + code);
                    continue;
                }
                if (!byCode.containsKey(dep)) {
                    errors.add("模块 '" + code + "' 依赖未注册的模块 '" + dep + "'");
                    continue;
                }
                if (dependents.get(dep).add(code)) {
                    indegree.merge(code, 1, Integer::sum);
                }
            }
        }

        PriorityQueue<String> ready = new PriorityQueue<>(Comparator.naturalOrder());
        for (Map.Entry<String, Integer> e : indegree.entrySet()) {
            if (e.getValue() == 0) {
                ready.offer(e.getKey());
            }
        }

        List<BusinessModuleDescriptor> sorted = new ArrayList<>(modules.size());
        while (!ready.isEmpty()) {
            String code = ready.poll();
            sorted.add(byCode.get(code));
            for (String child : dependents.get(code)) {
                int next = indegree.merge(child, -1, Integer::sum);
                if (next == 0) {
                    ready.offer(child);
                }
            }
        }

        if (sorted.size() != byCode.size()) {
            Set<String> resolved = new HashSet<>();
            for (BusinessModuleDescriptor d : sorted) {
                resolved.add(d.metadata().code());
            }
            for (String code : byCode.keySet()) {
                if (!resolved.contains(code)) {
                    List<String> cycle = findCycle(code, byCode, resolved);
                    if (cycle != null) {
                        errors.add("模块循环依赖: " + String.join(" -> ", cycle));
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            // 去重保持插入顺序
            LinkedHashMap<String, Boolean> dedup = new LinkedHashMap<>();
            for (String e : errors) {
                dedup.put(e, Boolean.TRUE);
            }
            throw new BusinessModuleConflictException(
                    "业务模块依赖图非法，启动阻断:\n  - " + String.join("\n  - ", dedup.keySet()));
        }
        return sorted;
    }

    /**
     * 从 start 出发做 DFS 找回到自己的最短环。返回环上的节点序列，首尾相同。
     * 若 start 实际并不在任何环里（理论上不会发生，因为它已被认定为未解节点），返回 null。
     */
    private List<String> findCycle(String start,
                                    Map<String, BusinessModuleDescriptor> byCode,
                                    Set<String> resolved) {
        Deque<String> path = new ArrayDeque<>();
        Set<String> onPath = new HashSet<>();
        if (dfs(start, start, byCode, resolved, path, onPath)) {
            List<String> cycle = new ArrayList<>(path);
            cycle.add(start);
            return cycle;
        }
        return null;
    }

    private boolean dfs(String start,
                         String node,
                         Map<String, BusinessModuleDescriptor> byCode,
                         Set<String> resolved,
                         Deque<String> path,
                         Set<String> onPath) {
        path.addLast(node);
        onPath.add(node);
        for (String dep : byCode.get(node).metadata().dependencies()) {
            if (resolved.contains(dep) || !byCode.containsKey(dep)) {
                continue;
            }
            if (dep.equals(start)) {
                return true;
            }
            if (onPath.contains(dep)) {
                continue;
            }
            if (dfs(start, dep, byCode, resolved, path, onPath)) {
                return true;
            }
        }
        path.removeLast();
        onPath.remove(node);
        return false;
    }
}
