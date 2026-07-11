package com.wensaas.core.api.dict;

/**
 * 字典分组与字典项的来源。
 *
 * <p>持久化到数据库时映射为 SMALLINT：SYSTEM=1 / MODULE=2 / CUSTOM=3。
 */
public enum DictSource {
    SYSTEM(1),
    MODULE(2),
    CUSTOM(3);

    private final int code;

    DictSource(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static DictSource fromCode(int code) {
        for (DictSource s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知 DictSource code: " + code);
    }
}
