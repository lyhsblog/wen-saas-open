package com.wensaas.core.impl.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_file} 表实体（混合表）。软删（@TableLogic deleted_at），
 * 物理清理由 {@code SoftDeletedFileCleanerJob} 在 30 天后执行。
 */
@TableName("sys_file")
@Getter
@Setter
public class SysFile {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String fileId;
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private String visibility;
    private String ownerModule;
    private String ownerRefType;
    private Long ownerRefId;
    private String folderPath;
    private String space;
    private Long folderId;
    private String backendType;
    private Long backendConfigId;
    private String backendObjectKey;
    private String uploadState;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
