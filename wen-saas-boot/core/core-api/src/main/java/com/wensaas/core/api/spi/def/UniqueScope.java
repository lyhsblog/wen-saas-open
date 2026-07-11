package com.wensaas.core.api.spi.def;

/**
 * 字段唯一性约束范围。
 *
 * <ul>
 *   <li>{@link #NONE} 不校验唯一</li>
 *   <li>{@link #IN_FILE} 仅文件内自冲突（底座可独立判定）</li>
 *   <li>{@link #IN_DB} 仅与已有数据冲突（需业务方 resolver）</li>
 *   <li>{@link #BOTH} 文件内 + 与已有数据均不可重复</li>
 * </ul>
 */
public enum UniqueScope {
    NONE,
    IN_FILE,
    IN_DB,
    BOTH;

    public boolean checksInFile() {
        return this == IN_FILE || this == BOTH;
    }

    public boolean checksInDb() {
        return this == IN_DB || this == BOTH;
    }
}
