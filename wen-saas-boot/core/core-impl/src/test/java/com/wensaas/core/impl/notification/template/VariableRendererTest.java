package com.wensaas.core.impl.notification.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VariableRendererTest {

    @Test
    void substitutes_simpleVariables() {
        String out = VariableRenderer.render("客户 {customer_name} 已 {days} 天未跟进",
                Map.of("customer_name", "王某某", "days", 30));
        assertThat(out).isEqualTo("客户 王某某 已 30 天未跟进");
    }

    @Test
    void preserves_undefined_placeholders() {
        // 变量未定义时显示原占位符（不报错、不跳过该消息）
        String out = VariableRenderer.render("Hello {name}, {missing}",
                Map.of("name", "Alice"));
        assertThat(out).isEqualTo("Hello Alice, {missing}");
    }

    @Test
    void null_or_empty_template_returnedAsIs() {
        assertThat(VariableRenderer.render(null, Map.of("x", "1"))).isNull();
        assertThat(VariableRenderer.render("", Map.of("x", "1"))).isEqualTo("");
    }

    @Test
    void null_or_empty_variables_returnTemplate() {
        assertThat(VariableRenderer.render("{x}", null)).isEqualTo("{x}");
        assertThat(VariableRenderer.render("{x}", Map.of())).isEqualTo("{x}");
    }

    @Test
    void chinese_braces_not_substituted() {
        // 变量名约束 ASCII；中文大括号或 {中文} 不匹配
        assertThat(VariableRenderer.render("{中文}", Map.of("中文", "value")))
                .isEqualTo("{中文}");
    }

    @Test
    void numeric_or_object_value_toStringed() {
        String out = VariableRenderer.render("{count} items, total {total}",
                Map.of("count", 5, "total", new java.math.BigDecimal("12.50")));
        assertThat(out).isEqualTo("5 items, total 12.50");
    }

    @Test
    void special_chars_dont_break_replacement() {
        // 替换值含 $ / \ 不影响（quoteReplacement 已处理）
        String out = VariableRenderer.render("path={p}",
                Map.of("p", "C:\\tmp\\$file"));
        assertThat(out).isEqualTo("path=C:\\tmp\\$file");
    }
}
