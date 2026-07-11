package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.auth.org.DeptAdminService;
import com.wensaas.core.api.auth.org.DeptAdminService.DeptView;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 内置导入 handler/resolver 共用工具（部门 name→id 映射等）。
 */
final class BuiltinImports {

    private BuiltinImports() {
    }

    /** 扁平化部门树为 name→id（重名取先遇到的）。 */
    static Map<String, Long> deptNameToId(DeptAdminService deptService) {
        Map<String, Long> map = new HashMap<>();
        deptService.listTree().forEach(d -> collect(d, map));
        return map;
    }

    private static void collect(DeptView d, Map<String, Long> map) {
        map.putIfAbsent(d.name(), d.id());
        d.children().forEach(c -> collect(c, map));
    }

    /** 按 keys 过滤映射（resolveReferences 取数口只返回命中的值）。 */
    static Map<String, Long> filterByKeys(Map<String, Long> all, Set<String> keys) {
        Map<String, Long> out = new HashMap<>();
        for (String k : keys) {
            Long id = all.get(k);
            if (id != null) {
                out.put(k, id);
            }
        }
        return out;
    }

    /** 去空白；空串归 {@code null}（可选字段传 null 而非空串）。 */
    static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    static Integer parseIntOrNull(String v) {
        String t = trimToNull(v);
        if (t == null) {
            return null;
        }
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
