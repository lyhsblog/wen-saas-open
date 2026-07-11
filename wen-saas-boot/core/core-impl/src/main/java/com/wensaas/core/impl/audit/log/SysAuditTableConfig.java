package com.wensaas.core.impl.audit.log;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_audit_table_config} ③ 列元数据。
 *
 * <p>一表双向驱动：PG 触发器读 {@code render}（MASK/EXCLUDE）决定"怎么记"；
 * 详情接口读 {@code label}/{@code render} 决定"怎么译"（成句）。
 *
 * <p>{@code column_name='*'} 为表级登记行（声明该表纳入审计），渲染时忽略。
 * {@code render}: {@code SCALAR | ENUM:<dictCode> | FK:<refTable.col> | MASK | EXCLUDE}。
 */
@TableName("sys_audit_table_config")
@Getter
@Setter
public class SysAuditTableConfig {

    private String tableName;
    private String columnName;
    private String label;
    private String render;

}
