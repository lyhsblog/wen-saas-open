package com.wensaas.core.impl.notification.template;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板变量替换器。
 *
 * <p>语法：{@code {variableName}} 简单字符串替换，不支持表达式。
 *
 * <p>未定义变量保留原占位符（不报错、不跳过该消息）。
 *
 * <p>变量名匹配：{@code [A-Za-z_][A-Za-z0-9_]*}（ASCII 字母数字下划线，首字符非数字）。
 * 其他字符（如中文 {{}}、JSON 占位）的大括号保留原文不替换。
 */
public final class VariableRenderer {

    /** 变量占位符正则：限制为 ASCII 标识符以避免误伤业务文案中的 JSON / 中文。 */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    private VariableRenderer() {}

    /**
     * 渲染单个字符串。
     *
     * @param template  模板原文
     * @param variables 变量值；null 视为空
     * @return 替换后字符串；template 为 null 时返回 null
     */
    public static String render(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        Matcher m = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            Object value = variables.get(name);
            String replacement;
            if (value == null) {
                // 保留原占位符；Matcher.appendReplacement 需要 quoteReplacement 防 $/\
                replacement = Matcher.quoteReplacement(m.group(0));
            } else {
                replacement = Matcher.quoteReplacement(value.toString());
            }
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
