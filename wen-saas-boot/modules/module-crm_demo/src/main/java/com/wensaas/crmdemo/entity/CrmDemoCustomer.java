package com.wensaas.crmdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.wensaas.core.api.customfield.CustomFieldExtension;
import com.wensaas.core.api.namesource.DisplayRef;
import com.wensaas.core.api.namesource.EntityTypes;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * CRM 客户实体（Demo）。
 *
 * <p>挂 {@link CustomFieldExtension} 接入自定义字段框架 v2：底座 {@code CustomFieldWriteInterceptor}
 * 在 INSERT/UPDATE 时校验 {@code extra}，{@code CustomFieldQueryRewriter} 在 SELECT 时把 WHERE 内
 * {@code cf_*} 列翻译为 {@code extra->>'cf_xxx'}。{@code autoResultMap=true} 供 jsonb Map 转换。
 */
@TableName(value = "crm_demo_customer", autoResultMap = true)
@CustomFieldExtension(objectCode = "crm_demo.customer")
@Getter
@Setter
public class CrmDemoCustomer {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private String name;
    private String code;

    /** 阶段（{@link CustomerStatus}）。 */
    private String status;

    /** 客户来源：字典分组 {@code crm_demo.customer_source} 的项 code；可空。 */
    private String source;

    /** 负责人 user_id（「我的客户」筛选）。{@code @DisplayRef} 让数据变更日志把 id 译成成员名。 */
    @DisplayRef(value = EntityTypes.SYS_USER, label = "负责人")
    private Long ownerId;

    /** 归属部门（数据权限 DEPT_AND_BELOW 列）。 */
    @DisplayRef(value = EntityTypes.SYS_DEPT, label = "归属部门")
    private Long ownerDeptId;

    /** 创建人（数据权限 SELF 列）。 */
    @DisplayRef(value = EntityTypes.SYS_USER, label = "创建人")
    private Long creatorId;

    /** 自定义字段 jsonb 容器。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;

    /** 进入当前阶段的时间（建档 / 推进时写，编辑不刷新）；「阶段停留超期提醒」的扫描依据。 */
    private LocalDateTime stageChangedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
