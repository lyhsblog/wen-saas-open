package com.wensaas.core.api.file.share;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 分享只读快照。{@code passwordHash} 永不返回；{@code hasPassword} 标记是否需要密码。
 *
 * @param shareToken         对外短链 ID（Base62 雪花）
 * @param fileId             被分享文件 ID
 * @param fileName           文件名（运营端 / 调用方展示）
 * @param mimeType           MIME 类型
 * @param sizeBytes          文件大小
 * @param expiresAt          过期时间；null = 永久
 * @param hasPassword        是否需要密码
 * @param maxDownloads       最大下载次数；null = 不限
 * @param downloadCount      已下载次数
 * @param remainingDownloads 剩余次数（maxDownloads-downloadCount）；null = 不限
 * @param status             active / revoked
 * @param createdAt          创建时间
 * @param createdBy          创建人 ID
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class FileShareInfo {
    private String shareToken;
    private String fileId;
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private LocalDateTime expiresAt;
    private boolean hasPassword;
    private Integer maxDownloads;
    private int downloadCount;
    private Integer remainingDownloads;
    private String status;
    private LocalDateTime createdAt;
    private Long createdBy;

}
