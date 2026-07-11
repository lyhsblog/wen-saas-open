package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.api.namesource.NameSearch;
import com.wensaas.core.api.namesource.CrossTenantView;
import com.wensaas.core.api.namesource.FillSkip;
import com.wensaas.core.api.namesource.TenantScope;
import com.wensaas.core.api.namesource.TransDescend;
import com.wensaas.core.api.namesource.TransField;
import com.wensaas.core.api.namesource.ValueTransform;
import com.wensaas.core.api.notification.access.RelatedObjectGuarded;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 回填框架单测共享夹具：样例响应 POJO（可变、纯字段，框架走反射，无需 Lombok）+ 计数桩 NameLookup。
 */
final class NameFillSamples {

    private NameFillSamples() {
    }

    static final String DEPT = "sys.dept";
    static final String ROLE = "sys.role";
    static final String USER = "sys.user";
    static final String BOGUS = "no.such"; // 未注册 type
    static final String COLOR = "demo.color";  // 自定义转换：code(String) → name(String)
    static final String ENTITY = "demo.entity"; // 自定义转换：code(String) → 实体对象

    /** 单值 + 多值 + 未注册 type 各一，覆盖回填全路径。 */
    static class Item {
        Long mainDeptId;
        @TransField(type = DEPT, ref = "mainDeptId")
        String mainDeptName;

        List<Long> roleIds;
        @TransField(type = ROLE, ref = "roleIds")
        List<String> roleNames;

        Long createdBy;
        @TransField(type = USER, ref = "createdBy")
        String createdByName;

        Long ghostId;
        @TransField(type = BOGUS, ref = "ghostId")
        String ghostName;

        Item(Long mainDeptId, List<Long> roleIds, Long createdBy, Long ghostId) {
            this.mainDeptId = mainDeptId;
            this.roleIds = roleIds;
            this.createdBy = createdBy;
            this.ghostId = ghostId;
        }
    }

    /** 两层嵌套：穿透 records 列表。 */
    static class Page {
        @TransDescend
        List<Item> records;
        String title; // 叶子字段，不下钻

        Page(List<Item> records, String title) {
            this.records = records;
            this.title = title;
        }
    }

    /** 三层嵌套：Wrapper → Page → records(List) → Item。 */
    static class Wrapper {
        @TransDescend
        Page page;

        Wrapper(Page page) {
            this.page = page;
        }
    }

    /** platform-only / 跨租户语义源引用项（验弱告警分支）。 */
    static class TenantRefItem {
        Long tenantId;
        @TransField(type = "sys.tenant", ref = "tenantId")
        String tenantName;

        TenantRefItem(Long tenantId) {
            this.tenantId = tenantId;
        }
    }

    /** 自引用，验证防环。 */
    static class Node {
        Long refId;
        @TransField(type = USER, ref = "refId")
        String refName;
        @TransDescend
        Node next;

        Node(Long refId) {
            this.refId = refId;
        }
    }

    /** 纯叶子：无 @TransField、无可下钻字段。 */
    static class Leaf {
        String a;
        int b;
    }

    /** 嵌套对象字段未标 @TransDescend → 声明式下不下钻（其内 @TransField 不回填）。 */
    static class UndeclaredNest {
        Item item; // 故意不标 @TransDescend

        UndeclaredNest(Item item) {
            this.item = item;
        }
    }

    /** @TransDescend 标在标量字段上 → 解析期 fail-fast。 */
    static class BadDescend {
        @TransDescend
        String notAnObject;
    }

    /** ref 指向不存在字段，触发 fail-fast。 */
    static class BadRef {
        Long x;
        @TransField(type = USER, ref = "doesNotExist")
        String name;
    }

    /** 运营专属视图（@CrossTenantView）：CROSS 字段合法。 */
    @CrossTenantView
    static class CrossView {
        Long uid;
        @TransField(type = USER, ref = "uid", scope = TenantScope.CROSS)
        String userName;

        CrossView(Long uid) {
            this.uid = uid;
        }
    }

    /** 误用：CROSS 字段但宿主类未标 @CrossTenantView → 解析期 fail-fast。 */
    static class CrossWithoutMarker {
        Long uid;
        @TransField(type = USER, ref = "uid", scope = TenantScope.CROSS)
        String userName;
    }

    /** 误用：@RelatedObjectGuarded 与 @TransField 同标一类 → 解析期 fail-fast（B3 互斥）。 */
    @RelatedObjectGuarded
    static class GuardedWithTransField {
        Long uid;
        @TransField(type = USER, ref = "uid")
        String userName;
    }

    /** 各 skipIf 跳过模式各一字段，覆盖条件回填全路径（字段公开供单测直接置原值）。 */
    static class SkipSamples {
        // 目标已有内容 → 跳过；目标 null / 空白 → 回填
        Long deptId;
        @TransField(type = DEPT, ref = "deptId", skipIf = FillSkip.TARGET_NOT_BLANK)
        String deptName;

        // 目标 != null（含空串）→ 跳过（区别于 NOT_BLANK）
        Long deptId2;
        @TransField(type = DEPT, ref = "deptId2", skipIf = FillSkip.TARGET_NOT_NULL)
        String deptName2;

        // 来源标量 0（哨兵）→ 跳过
        Long userId;
        @TransField(type = USER, ref = "userId", skipIf = FillSkip.SOURCE_ZERO)
        String userName;

        // 来源空集合 → 跳过，保留目标列表原值（默认会写成空列表）
        List<Long> roleIds;
        @TransField(type = ROLE, ref = "roleIds", skipIf = FillSkip.SOURCE_BLANK)
        List<String> roleNames;

        // OR：目标已有内容 或 来源 null，命中任一即跳过
        Long deptId3;
        @TransField(type = DEPT, ref = "deptId3", skipIf = {FillSkip.TARGET_NOT_BLANK, FillSkip.SOURCE_NULL})
        String deptName3;
    }

    /** 单字段、TARGET_NOT_BLANK：整列表都预置原值时验「该 type 翻译被整体省掉」(calls==0)。 */
    static class SkipAllDept {
        Long deptId;
        @TransField(type = DEPT, ref = "deptId", skipIf = FillSkip.TARGET_NOT_BLANK)
        String deptName;

        SkipAllDept(Long deptId, String deptName) {
            this.deptId = deptId;
            this.deptName = deptName;
        }
    }

    /** 多值目标的多种容器形态：List / Set / 数组，且 ref 覆盖 Collection / Long[] / long[]。 */
    static class MultiShapes {
        List<Long> roleIdsList;
        @TransField(type = ROLE, ref = "roleIdsList")
        List<String> roleNamesList;

        List<Long> roleIdsForSet;
        @TransField(type = ROLE, ref = "roleIdsForSet")
        Set<String> roleNamesSet;

        Long[] roleIdsBoxed;
        @TransField(type = ROLE, ref = "roleIdsBoxed")
        String[] roleNamesArray;

        long[] roleIdsPrim;
        @TransField(type = ROLE, ref = "roleIdsPrim")
        List<String> roleNamesFromPrim;

        // 宽容判定：HashSet 字段吃 LinkedHashSet（子类）、TreeSet 字段吃 TreeSet、Collection 吃 ArrayList
        List<Long> roleIdsForHashSet;
        @TransField(type = ROLE, ref = "roleIdsForHashSet")
        HashSet<String> roleNamesHashSet;

        List<Long> roleIdsForTreeSet;
        @TransField(type = ROLE, ref = "roleIdsForTreeSet")
        TreeSet<String> roleNamesTreeSet;

        List<Long> roleIdsForColl;
        @TransField(type = ROLE, ref = "roleIdsForColl")
        Collection<String> roleNamesColl;
    }

    /** 自定义逻辑转换目标：String 键 + 非 String 值（code→entity），验 TransFiller 不假定键值类型。 */
    record Demo(String code, String label) {
    }

    static class CustomItem {
        String colorCode; // String 键
        @TransField(type = COLOR, ref = "colorCode")
        String colorName;

        String entityCode; // String 键 → 对象值
        @TransField(type = ENTITY, ref = "entityCode")
        Demo entity;

        List<String> colorCodes; // String 集合键 → 多值
        @TransField(type = COLOR, ref = "colorCodes")
        List<String> colorNames;
    }

    /** 自定义转换计数桩：按表翻译、记 transform 调用次数（验聚合 / 去重）。键值任意类型。 */
    static final class MapTransform implements ValueTransform {
        private final String type;
        private final Map<Object, Object> table;
        int calls = 0;

        MapTransform(String type, Map<Object, Object> table) {
            this.type = type;
            this.table = table;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public Map<Object, Object> transform(Collection<Object> keys) {
            calls++;
            Map<Object, Object> out = new HashMap<>();
            for (Object k : keys) {
                Object v = table.get(k);
                if (v != null) {
                    out.put(k, v);
                }
            }
            return out;
        }
    }

    /** platform-only 桩（tenantSearchable=false），batchLookup 恒空，模拟「漏设 scope=CROSS→查空」触发弱告警。 */
    static final class PlatformOnlyLookup implements NameLookup, NameSearch {
        private final String type;

        PlatformOnlyLookup(String type) {
            this.type = type;
        }

        @Override
        public String entityType() {
            return type;
        }

        @Override
        public String displayLabel() {
            return type;
        }

        @Override
        public Map<Long, String> batchLookup(Collection<Long> ids) {
            return Map.of();
        }

        @Override
        public boolean tenantSearchable() {
            return false;
        }

        @Override
        public List<FieldMeta> searchFields() {
            return List.of();
        }

        @Override
        public List<NameHit> search(SearchQuery query) {
            return List.of();
        }
    }

    /** 计数桩：按固定表翻译，记录 batchLookup 调用次数（验 N+1 / 去重）。 */
    static final class CountingLookup implements NameLookup {
        private final String type;
        private final Map<Long, String> table;
        int calls = 0;

        CountingLookup(String type, Map<Long, String> table) {
            this.type = type;
            this.table = table;
        }

        @Override
        public String entityType() {
            return type;
        }

        @Override
        public String displayLabel() {
            return type;
        }

        @Override
        public Map<Long, String> batchLookup(Collection<Long> ids) {
            calls++;
            Map<Long, String> out = new HashMap<>();
            for (Long id : ids) {
                String n = table.get(id);
                if (n != null) {
                    out.put(id, n);
                }
            }
            return out;
        }
    }
}
