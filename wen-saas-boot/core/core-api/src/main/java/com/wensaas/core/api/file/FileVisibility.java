package com.wensaas.core.api.file;

/**
 * 文件可见性，划定"文件中心 vs 业务文件"的边界。
 *
 * <ul>
 *   <li>{@link #FILE_CENTER}：文件中心可见（用户上传到"我的/共享"目录）</li>
 *   <li>{@link #BUSINESS_ONLY}：仅业务模块详情页可见，文件中心不展示</li>
 * </ul>
 */
public enum FileVisibility {
    FILE_CENTER("file-center"),
    BUSINESS_ONLY("business-only");

    private final String code;

    FileVisibility(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static FileVisibility fromCode(String code) {
        for (FileVisibility v : values()) {
            if (v.code.equals(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知 FileVisibility code: " + code);
    }
}
