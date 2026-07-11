package com.wensaas.core.api.file;

/**
 * 存储后端类型。
 *
 * <p>{@link #LOCAL}（开发/测试）、{@link #ALIYUN_OSS}（正式默认）、{@link #TENCENT_COS}（可选）；
 * 华为云 OBS / AWS S3 / MinIO 留 P1。
 */
public enum FileBackendType {
    LOCAL("local"),
    ALIYUN_OSS("aliyun_oss"),
    TENCENT_COS("tencent_cos");

    private final String code;

    FileBackendType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static FileBackendType fromCode(String code) {
        for (FileBackendType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知 FileBackendType code: " + code);
    }
}
