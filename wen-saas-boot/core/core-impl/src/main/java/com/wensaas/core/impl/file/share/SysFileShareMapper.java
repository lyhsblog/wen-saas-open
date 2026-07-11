package com.wensaas.core.impl.file.share;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysFileShareMapper extends BaseMapper<SysFileShare> {

    /** 按 share_token 找活跃记录（MP @TableLogic 自动加 deleted_at IS NULL）。 */
    SysFileShare selectByToken(@Param("shareToken") String shareToken);

    /** 列文件当前租户下所有活跃分享（按 created_at DESC）。 */
    List<SysFileShare> selectAliveByFileId(@Param("tenantId") Long tenantId,
                                           @Param("fileId") String fileId);

    /**
     * 原子消费一次下载：UPDATE WHERE active + 未过期 + 未超限。返回 affected rows。
     * affected=0 表示并发争用 / 已过期 / 已撤销 / 超限，由 Service 层 SELECT 区分。
     */
    int tryConsume(@Param("shareToken") String shareToken,
                   @Param("now") LocalDateTime now);
}
