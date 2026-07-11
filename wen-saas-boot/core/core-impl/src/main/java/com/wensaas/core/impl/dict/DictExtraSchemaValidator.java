package com.wensaas.core.impl.dict;

import com.wensaas.core.api.spi.def.DictExtraDataType;
import com.wensaas.core.api.spi.def.DictExtraFieldDef;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 字典分组扩展字段 schema 校验。
 *
 * <p>v1 调用点：{@link DictSpiSynchronizer} 在 INSERT 模块预置项前调用。
 * 后续 CRUD UI 会话可复用相同入口校验运营/租户写入。
 */
public class DictExtraSchemaValidator {

    private static final Pattern URL_PATTERN =
            Pattern.compile("^(https?|ftp)://[^\\s]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_PATTERN =
            Pattern.compile("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$");

    /**
     * 校验一组扩展字段值是否合法。
     *
     * @param schema 字段 schema 定义；null/空 表示分组无扩展，{@code values} 任意（仅会忽略其余字段）
     * @param values 待校验的字段值（可空，等同于全空）
     * @throws IllegalArgumentException 必填字段缺失或类型不匹配时抛出，message 含具体字段名
     */
    public void validate(List<DictExtraFieldDef> schema, Map<String, Object> values) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        Map<String, Object> safeValues = values == null ? Map.of() : values;
        for (DictExtraFieldDef def : schema) {
            Object v = safeValues.get(def.fieldCode());
            if (v == null) {
                if (def.required()) {
                    throw new IllegalArgumentException(
                            "扩展字段 '" + def.fieldCode() + "' 必填但未提供值");
                }
                continue;
            }
            checkType(def.fieldCode(), def.dataType(), v);
        }
    }

    private static void checkType(String fieldCode, DictExtraDataType type, Object v) {
        switch (type) {
            case TEXT, ICON -> requireType(fieldCode, type, v, v instanceof String);
            case NUMBER -> requireType(fieldCode, type, v, v instanceof Number);
            case BOOL -> requireType(fieldCode, type, v, v instanceof Boolean);
            case URL -> {
                requireType(fieldCode, type, v, v instanceof String);
                if (!URL_PATTERN.matcher((String) v).matches()) {
                    throw new IllegalArgumentException(
                            "扩展字段 '" + fieldCode + "' URL 格式不合法: " + v);
                }
            }
            case COLOR -> {
                requireType(fieldCode, type, v, v instanceof String);
                if (!COLOR_PATTERN.matcher((String) v).matches()) {
                    throw new IllegalArgumentException(
                            "扩展字段 '" + fieldCode + "' COLOR 必须形如 #RRGGBB 或 #RGB: " + v);
                }
            }
        }
    }

    private static void requireType(String fieldCode, DictExtraDataType type, Object v, boolean ok) {
        if (!ok) {
            throw new IllegalArgumentException(
                    "扩展字段 '" + fieldCode + "' 期望 " + type + "，实际 " + v.getClass().getSimpleName());
        }
    }
}
