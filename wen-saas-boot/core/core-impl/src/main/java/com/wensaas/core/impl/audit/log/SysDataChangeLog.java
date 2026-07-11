package com.wensaas.core.impl.audit.log;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_data_change_log} ③ 数据变更日志读模型。
 *
 * <p>由 PG 触发器 {@code sys_audit_row_change()} 写入，应用层只读。
 * {@code changes} 在查询时以 {@code ::text} 取原始 jsonb 文本（{@code {列:{before,after}}}），
 * 展示层按 {@code sys_audit_table_config} 列字典渲染成句。
 */
@TableName("sys_data_change_log")
@Getter
@Setter
public class SysDataChangeLog {

    private Long id;
    private String requestId;
    private Long actorId;
    private Long tenantId;
    private String tableName;
    private String op;
    private String rowPk;
    private String changes;
    private LocalDateTime createdAt;

}
