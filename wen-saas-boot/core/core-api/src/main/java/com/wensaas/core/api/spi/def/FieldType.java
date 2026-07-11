package com.wensaas.core.api.spi.def;

/**
 * 导入字段数据类型。
 *
 * <ul>
 *   <li>{@link #STRING} 文本</li>
 *   <li>{@link #INTEGER} 整数</li>
 *   <li>{@link #NUMBER} 数值（含小数）</li>
 *   <li>{@link #DATE} 日期（按 {@code format} 指定模式解析）</li>
 *   <li>{@link #BOOLEAN} 布尔（是/否、true/false、1/0）</li>
 *   <li>{@link #DICT} 字典编码（取值校验交由业务方 resolver 或 reference）</li>
 * </ul>
 */
public enum FieldType {
    STRING,
    INTEGER,
    NUMBER,
    DATE,
    BOOLEAN,
    DICT
}
