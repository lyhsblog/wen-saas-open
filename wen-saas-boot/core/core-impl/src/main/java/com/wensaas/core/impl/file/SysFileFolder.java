package com.wensaas.core.impl.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_file_folder} 表实体（混合表）。文件中心文件夹树。
 *
 * <p>软删（@TableLogic deleted_at）；删文件夹级联软删整棵子树 + 其下文件，30 天后物理清理。
 */
@TableName("sys_file_folder")
@Getter
@Setter
public class SysFileFolder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String space;
    private Long parentId;
    private String name;
    private Integer depth;
    private Long ownerId;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
