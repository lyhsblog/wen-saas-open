package com.wensaas.core.impl.file.share;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_file_share} 表实体（混合表）。
 * 撤销同步软删（@TableLogic deleted_at）；过期由 expires_at 时间判定，不改 status。
 */
@TableName("sys_file_share")
@Getter
@Setter
public class SysFileShare {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String shareToken;
    private String fileId;
    private LocalDateTime expiresAt;
    private String passwordHash;
    private Integer maxDownloads;
    private Integer downloadCount;
    private String status;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
