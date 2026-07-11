package com.wensaas.core.api.file.center;

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
 * 回收站条目（文件或文件夹「删除根」）。
 *
 * @param type      FILE / FOLDER
 * @param id        文件为 fileId（字符串）；文件夹为雪花 id（序列化为字符串）
 * @param purgeAt   预计彻底清理时间（deletedAt + 30 天）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class RecycleItem {
    private String type;
    private String id;
    private String name;
    private long sizeBytes;
    private LocalDateTime deletedAt;
    private LocalDateTime purgeAt;

    public static final String TYPE_FILE = "FILE";
    public static final String TYPE_FOLDER = "FOLDER";
}
