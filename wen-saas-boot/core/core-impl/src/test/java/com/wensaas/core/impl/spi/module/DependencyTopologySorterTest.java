package com.wensaas.core.impl.spi.module;

import com.wensaas.core.api.spi.BusinessModuleConflictException;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.ModuleMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyTopologySorterTest {

    private final DependencyTopologySorter sorter = new DependencyTopologySorter();

    @Test
    void emptyInput_returnsEmpty() {
        assertThat(sorter.sort(List.of())).isEmpty();
    }

    @Test
    void singleChain_sortedByDependency() {
        // c 依赖 b，b 依赖 a → 出队顺序 a, b, c
        var a = module("a");
        var b = module("b", "a");
        var c = module("c", "b");
        List<BusinessModuleDescriptor> sorted = sorter.sort(List.of(c, b, a));
        assertThat(codes(sorted)).containsExactly("a", "b", "c");
    }

    @Test
    void sameIndegree_sortedByCodeLexicographically() {
        // 三个无依赖模块输入乱序 → 字典序输出
        var x = module("x");
        var a = module("a");
        var m = module("m");
        List<BusinessModuleDescriptor> sorted = sorter.sort(List.of(x, m, a));
        assertThat(codes(sorted)).containsExactly("a", "m", "x");
    }

    @Test
    void multiDependency_topologicallySorted() {
        // c 依赖 a 与 b；a、b 无依赖 → c 排在 a、b 之后
        var a = module("a");
        var b = module("b");
        var c = module("c", "a", "b");
        List<BusinessModuleDescriptor> sorted = sorter.sort(List.of(c, a, b));
        assertThat(codes(sorted)).containsExactly("a", "b", "c");
    }

    @Test
    void selfDependency_throwsAsCycle() {
        var a = module("a", "a");
        assertThatThrownBy(() -> sorter.sort(List.of(a)))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("模块循环依赖")
                .hasMessageContaining("a -> a");
    }

    @Test
    void mutualCycle_throws() {
        var a = module("a", "b");
        var b = module("b", "a");
        assertThatThrownBy(() -> sorter.sort(List.of(a, b)))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("模块循环依赖");
    }

    @Test
    void unknownDependency_throws() {
        var crm = module("crm", "foundation");
        assertThatThrownBy(() -> sorter.sort(List.of(crm)))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("crm")
                .hasMessageContaining("foundation");
    }

    private static BusinessModuleDescriptor module(String code, String... deps) {
        return new BusinessModuleDescriptor() {
            @Override
            public ModuleMetadata metadata() {
                return new ModuleMetadata(code, code, null, "1.0.0", null, List.of(deps));
            }
        };
    }

    private static List<String> codes(List<BusinessModuleDescriptor> modules) {
        return modules.stream().map(d -> d.metadata().code()).toList();
    }
}
