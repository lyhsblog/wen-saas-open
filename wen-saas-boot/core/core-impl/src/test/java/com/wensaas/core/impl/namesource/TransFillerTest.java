package com.wensaas.core.impl.namesource;

import com.wensaas.core.impl.namesource.NameFillSamples.CountingLookup;
import com.wensaas.core.impl.namesource.NameFillSamples.CustomItem;
import com.wensaas.core.impl.namesource.NameFillSamples.Demo;
import com.wensaas.core.impl.namesource.NameFillSamples.Item;
import com.wensaas.core.impl.namesource.NameFillSamples.MapTransform;
import com.wensaas.core.impl.namesource.NameFillSamples.MultiShapes;
import com.wensaas.core.impl.namesource.NameFillSamples.Node;
import com.wensaas.core.impl.namesource.NameFillSamples.Page;
import com.wensaas.core.impl.namesource.NameFillSamples.PlatformOnlyLookup;
import com.wensaas.core.impl.namesource.NameFillSamples.SkipAllDept;
import com.wensaas.core.impl.namesource.NameFillSamples.SkipSamples;
import com.wensaas.core.impl.namesource.NameFillSamples.TenantRefItem;
import com.wensaas.core.impl.namesource.NameFillSamples.Wrapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransFillerTest {

    private CountingLookup deptLookup;
    private CountingLookup roleLookup;
    private CountingLookup userLookup;

    private TransFiller newFiller() {
        deptLookup = new CountingLookup(NameFillSamples.DEPT, Map.of(10L, "研发中心", 11L, "后端组"));
        roleLookup = new CountingLookup(NameFillSamples.ROLE, Map.of(1L, "管理员", 2L, "审计员"));
        userLookup = new CountingLookup(NameFillSamples.USER, Map.of(100L, "张三", 200L, "李四"));
        EntityNameResolver resolver = new EntityNameResolver(List.of(deptLookup, roleLookup, userLookup));
        return new TransFiller(resolver, new TransSpecRegistry(), new ValueTransformRegistry(List.of(), resolver));
    }

    @Test
    void fillsSingleAndCollectionRefs_inPlace() {
        TransFiller filler = newFiller();
        Item it = new Item(10L, List.of(1L, 2L), 100L, 999L);

        filler.fill(it);

        assertThat(it.mainDeptName).isEqualTo("研发中心");
        assertThat(it.roleNames).containsExactly("管理员", "审计员");
        assertThat(it.createdByName).isEqualTo("张三");
        // ghostId 引用未注册 type → 留 null；id 兄弟字段不变
        assertThat(it.ghostName).isNull();
        assertThat(it.ghostId).isEqualTo(999L);
    }

    @Test
    void collectionRef_filtersUntranslatable_preservesOrder() {
        TransFiller filler = newFiller();
        Item it = new Item(10L, new ArrayList<>(List.of(1L, 99L, 2L)), 100L, null);

        filler.fill(it);

        // 99 翻不出被过滤，其余按序保留（与历史 .filter(nonNull) 一致）
        assertThat(it.roleNames).containsExactly("管理员", "审计员");
    }

    @Test
    void nullCollectionRef_leavesTargetNull() {
        TransFiller filler = newFiller();
        Item it = new Item(10L, null, 100L, null);

        filler.fill(it);

        assertThat(it.roleNames).isNull();
        assertThat(it.mainDeptName).isEqualTo("研发中心");
    }

    @Test
    void missingId_leavesNameNull_idUntouchedAsFallback() {
        TransFiller filler = newFiller();
        Item it = new Item(404L, List.of(), 404L, null); // dept/user 都翻不出

        filler.fill(it);

        assertThat(it.mainDeptName).isNull();
        assertThat(it.mainDeptId).isEqualTo(404L);     // id 原样保留
        assertThat(it.createdByName).isNull();
        assertThat(it.roleNames).isEmpty();            // 空集合 ref → 空列表
    }

    @Test
    void batchDedup_oneResolvePerType_regardlessOfRowCount() {
        TransFiller filler = newFiller();
        List<Item> rows = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            rows.add(new Item(10L, List.of(1L), 100L, null)); // 同 id 跨行重复
        }

        filler.fill(rows);

        // N 行 × 3 类型 → 每类型恰 1 次 batchLookup（去重 + 批量，N+1 回归）
        assertThat(deptLookup.calls).isEqualTo(1);
        assertThat(roleLookup.calls).isEqualTo(1);
        assertThat(userLookup.calls).isEqualTo(1);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.mainDeptName).isEqualTo("研发中心");
            assertThat(r.roleNames).containsExactly("管理员");
            assertThat(r.createdByName).isEqualTo("张三");
        });
    }

    @Test
    void deepRecursion_fillsNestedRecords_inSingleFill() {
        TransFiller filler = newFiller();
        Page page = new Page(List.of(
                new Item(10L, List.of(1L), 100L, null),
                new Item(11L, List.of(2L), 200L, null)), "成员列表");

        filler.fill(page);

        assertThat(page.records.get(0).mainDeptName).isEqualTo("研发中心");
        assertThat(page.records.get(0).createdByName).isEqualTo("张三");
        assertThat(page.records.get(1).mainDeptName).isEqualTo("后端组");
        assertThat(page.records.get(1).createdByName).isEqualTo("李四");
        // 嵌套下批量语义不变：2 行不同 id 仍各类型 1 次
        assertThat(deptLookup.calls).isEqualTo(1);
        assertThat(userLookup.calls).isEqualTo(1);
    }

    @Test
    void undeclaredNestedField_notDescended() {
        TransFiller filler = newFiller();
        Item inner = new Item(10L, List.of(1L), 100L, null);
        NameFillSamples.UndeclaredNest n = new NameFillSamples.UndeclaredNest(inner);

        filler.fill(n); // item 字段未标 @TransDescend → 不下钻

        assertThat(inner.mainDeptName).isNull();
        assertThat(inner.roleNames).isNull();
        assertThat(deptLookup.calls).isZero();
        assertThat(roleLookup.calls).isZero();
    }

    @Test
    void threeLayerNesting_fillsDeepestItems_batchOnce() {
        TransFiller filler = newFiller();
        Wrapper w = new Wrapper(new Page(List.of(
                new Item(10L, List.of(1L), 100L, null),
                new Item(11L, List.of(2L), 200L, null)), "页"));

        filler.fill(w); // Wrapper → Page → records → Item，一次 fill 穿透三层

        assertThat(w.page.records.get(0).mainDeptName).isEqualTo("研发中心");
        assertThat(w.page.records.get(0).roleNames).containsExactly("管理员");
        assertThat(w.page.records.get(1).mainDeptName).isEqualTo("后端组");
        assertThat(w.page.records.get(1).createdByName).isEqualTo("李四");
        // 三层下批量语义不变
        assertThat(deptLookup.calls).isEqualTo(1);
        assertThat(userLookup.calls).isEqualTo(1);
        assertThat(roleLookup.calls).isEqualTo(1);
    }

    @Test
    void platformOnlySource_emptyResult_leavesNull_noThrow() {
        // platform-only 源（tenantSearchable=false）查空 → 触发弱告警分支：只 warn 不抛、名留 null
        PlatformOnlyLookup po = new PlatformOnlyLookup("sys.tenant");
        EntityNameResolver r = new EntityNameResolver(List.of(po));
        TransFiller f = new TransFiller(r, new TransSpecRegistry(), new ValueTransformRegistry(List.of(), r));
        assertThat(r.isPlatformOnly("sys.tenant")).isTrue();

        TenantRefItem it = new TenantRefItem(5L);
        f.fill(it);

        assertThat(it.tenantName).isNull();
        assertThat(it.tenantId).isEqualTo(5L);
    }

    @Test
    void cyclicGraph_terminates_andFillsBoth() {
        TransFiller filler = newFiller();
        Node a = new Node(100L);
        Node b = new Node(200L);
        a.next = b;
        b.next = a; // 环

        filler.fill(a); // 不应栈溢出 / 死循环

        assertThat(a.refName).isEqualTo("张三");
        assertThat(b.refName).isEqualTo("李四");
    }

    @Test
    void nullRoot_noop() {
        TransFiller filler = newFiller();
        filler.fill((Object) null); // 不抛
        assertThat(deptLookup.calls).isZero();
    }

    // ===================== 多值容器形态（List / Set / 数组）=====================

    @Test
    void multiTargets_materializeByShape_listSetArray() {
        TransFiller filler = newFiller();
        MultiShapes m = new MultiShapes();
        m.roleIdsList = List.of(1L, 2L);
        m.roleIdsForSet = List.of(1L, 2L, 1L); // 含重复，Set 去重保序
        m.roleIdsBoxed = new Long[]{2L, 1L};
        m.roleIdsPrim = new long[]{1L, 99L, 2L}; // 99 翻不出过滤

        filler.fill(m);

        assertThat(m.roleNamesList).isInstanceOf(java.util.ArrayList.class)
                .containsExactly("管理员", "审计员");
        assertThat(m.roleNamesSet).isInstanceOf(java.util.LinkedHashSet.class)
                .containsExactly("管理员", "审计员"); // 去重 + 保序
        assertThat(m.roleNamesArray).containsExactly("审计员", "管理员"); // 数组保序
        assertThat(m.roleNamesFromPrim).containsExactly("管理员", "审计员");
        // 四字段同 type 同 scope → 聚合一次 resolve
        assertThat(roleLookup.calls).isEqualTo(1);
    }

    @Test
    void multiTargets_assignabilityLenient_hashSetTreeSetCollection() {
        TransFiller filler = newFiller();
        MultiShapes m = new MultiShapes();
        m.roleIdsForHashSet = List.of(1L, 2L);
        m.roleIdsForTreeSet = List.of(2L, 1L);
        m.roleIdsForColl = List.of(1L, 2L);

        filler.fill(m);

        // HashSet 字段被 LinkedHashSet（子类）填充——用实现可赋值判定，比写死映射宽容
        assertThat(m.roleNamesHashSet).isInstanceOf(java.util.LinkedHashSet.class)
                .containsExactlyInAnyOrder("管理员", "审计员");
        assertThat(m.roleNamesTreeSet).isInstanceOf(java.util.TreeSet.class)
                .containsExactly("审计员", "管理员"); // TreeSet 自然序：审<管
        assertThat(m.roleNamesColl).isInstanceOf(java.util.ArrayList.class)
                .containsExactly("管理员", "审计员");
    }

    // ===================== 自定义逻辑转换（ValueTransform，键值任意类型）=====================

    @Test
    void customValueTransform_stringKey_objectValue_dispatchedAndAggregated() {
        EntityNameResolver resolver = new EntityNameResolver(List.of());
        MapTransform color = new MapTransform(NameFillSamples.COLOR, Map.of("R", "红", "G", "绿"));
        MapTransform entity = new MapTransform(NameFillSamples.ENTITY, Map.of("X", new Demo("X", "实体X")));
        ValueTransformRegistry reg = new ValueTransformRegistry(List.of(color, entity), resolver);
        TransFiller filler = new TransFiller(resolver, new TransSpecRegistry(), reg);

        CustomItem it = new CustomItem();
        it.colorCode = "R";                       // String 键 → String 值
        it.entityCode = "X";                      // String 键 → 对象值（code→entity）
        it.colorCodes = List.of("R", "G", "Z");   // Z 翻不出过滤

        filler.fill(it);

        assertThat(it.colorName).isEqualTo("红");
        assertThat(it.entity).isEqualTo(new Demo("X", "实体X"));
        assertThat(it.colorNames).containsExactly("红", "绿");
        assertThat(color.calls).isEqualTo(1);     // 单值 colorCode + 多值 colorCodes 同 type 聚合一次
        assertThat(entity.calls).isEqualTo(1);
    }

    @Test
    void valueTransformTypeClashingWithNameLookup_failFast() {
        EntityNameResolver resolver = new EntityNameResolver(List.of(
                new CountingLookup(NameFillSamples.ROLE, Map.of())));
        MapTransform clash = new MapTransform(NameFillSamples.ROLE, Map.of()); // 与表引用同 type
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ValueTransformRegistry(List.of(clash), resolver))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("冲突");
    }

    // ===================== 跳过模式（skipIf）=====================

    @Test
    void noSkipIf_overwritesPresetTarget_regression() {
        TransFiller filler = newFiller();
        Item it = new Item(10L, List.of(1L), 100L, null);
        it.mainDeptName = "旧值"; // 无 skipIf → 仍无条件覆盖（与改造前一致）

        filler.fill(it);

        assertThat(it.mainDeptName).isEqualTo("研发中心");
    }

    @Test
    void skipTargetNotBlank_preservesContent_fillsBlank() {
        TransFiller filler = newFiller();
        SkipSamples preset = new SkipSamples();
        preset.deptId = 10L;
        preset.deptName = "手填部门"; // 有内容 → 跳过

        SkipSamples blank = new SkipSamples();
        blank.deptId = 10L;
        blank.deptName = "  ";        // 空白 → 回填

        filler.fill(List.of(preset, blank));

        assertThat(preset.deptName).isEqualTo("手填部门");
        assertThat(blank.deptName).isEqualTo("研发中心");
    }

    @Test
    void skipTargetNotNull_skipsEmptyString_distinctFromBlank() {
        TransFiller filler = newFiller();
        SkipSamples s = new SkipSamples();
        s.deptId2 = 10L;
        s.deptName2 = ""; // 空串但 != null → NOT_NULL 命中 → 跳过（NOT_BLANK 不会跳）

        filler.fill(s);

        assertThat(s.deptName2).isEmpty();
    }

    @Test
    void skipSourceZero_sentinelSkipped_realIdFilled() {
        TransFiller filler = newFiller();
        SkipSamples zero = new SkipSamples();
        zero.userId = 0L; // 哨兵 → 跳过

        SkipSamples real = new SkipSamples();
        real.userId = 100L;

        filler.fill(List.of(zero, real));

        assertThat(zero.userName).isNull();
        assertThat(real.userName).isEqualTo("张三");
    }

    @Test
    void skipSourceBlank_multi_preservesTargetList() {
        TransFiller filler = newFiller();
        SkipSamples empty = new SkipSamples();
        empty.roleIds = List.of();                 // 空集合 → 跳过
        empty.roleNames = List.of("原值");          // 不被写成空列表

        SkipSamples filled = new SkipSamples();
        filled.roleIds = List.of(1L, 2L);

        filler.fill(List.of(empty, filled));

        assertThat(empty.roleNames).containsExactly("原值");
        assertThat(filled.roleNames).containsExactly("管理员", "审计员");
    }

    @Test
    void skipOr_anyConditionTriggersSkip() {
        TransFiller filler = newFiller();
        SkipSamples byTarget = new SkipSamples();  // TARGET_NOT_BLANK 命中
        byTarget.deptId3 = 10L;
        byTarget.deptName3 = "已有";

        SkipSamples bySource = new SkipSamples();  // SOURCE_NULL 命中
        bySource.deptId3 = null;

        SkipSamples neither = new SkipSamples();   // 两条都不命中 → 回填
        neither.deptId3 = 10L;

        filler.fill(List.of(byTarget, bySource, neither));

        assertThat(byTarget.deptName3).isEqualTo("已有");
        assertThat(bySource.deptName3).isNull();
        assertThat(neither.deptName3).isEqualTo("研发中心");
    }

    @Test
    void skipAtCollect_omitsTypeEntirely_noBatchLookup() {
        TransFiller filler = newFiller();
        List<SkipAllDept> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(new SkipAllDept(10L, "已有" + i)); // 整列表都跳过
        }

        filler.fill(rows);

        assertThat(deptLookup.calls).isZero(); // 该 type 翻译被整体省掉
        assertThat(rows).allSatisfy(r -> assertThat(r.deptName).startsWith("已有"));
    }
}
