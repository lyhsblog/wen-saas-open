package com.wensaas.core.api.compliance.retention;

/**
 * 数据留存与软删除策略服务。运行在租户上下文，读取当前租户回收站口径。
 */
public interface RetentionService {

    /** 留存策略总览：声明式策略矩阵 + 当前租户文件回收站实时统计 + 自动清理节奏。 */
    RetentionOverview overview();
}
