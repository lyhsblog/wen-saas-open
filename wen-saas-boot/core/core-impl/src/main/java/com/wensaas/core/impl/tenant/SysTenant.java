package com.wensaas.core.impl.tenant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.api.namesource.DisplayRef;
import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.tenant.TenantPlan;
import com.wensaas.core.impl.tenant.PgJsonbTypeHandler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_tenant 表实体（纯平台表，不带 tenant_id 字段）。
 *
 * <p>v1 仅承载主管理员判定 + 套餐 plans 所需字段；后续运营后台租户管理会话再补全 CRUD 实体定义。
 */
@TableName(value = "sys_tenant", autoResultMap = true)
@Getter
@Setter
public class SysTenant {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String code;
    private String name;
    private Integer status;
    private Long mainAdminUserId;
    private String logoUrl;
    private String description;
    /** 运营内部编号（全局唯一）。 */
    private String tenantNo;
    /** 运营备注（内部可见）。 */
    private String remark;
    /** 已勾选套餐数组。详见 {@link TenantPlan}。 */
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private List<TenantPlan> plans;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    /** 租户级有效期（NULL=永久）；{@code TenantExpireJob} 据此自动置 status=2。列类型 TIMESTAMPTZ，映射为 Instant。 */
    private Instant expireAt;
    /** 客户经理（运营账号 sys_user.id，非必填）。 */
    @DisplayRef(value = EntityTypes.SYS_USER, label = "CSM")
    private Long csmUserId;
    /** 成员数上限（NULL=不限制）。 */
    private Integer memberCap;
    /** 存储上限字节数（NULL=不限制）。 */
    private Long storageCap;
    /** 租户时区 IANA 标识（NULL=按平台默认）。 */
    private String timezone;
    /** 公司全称。 */
    private String fullName;
    /** 统一社会信用代码。 */
    private String uscc;
    /** 行业。 */
    private String industry;
    /** 规模。 */
    private String scale;
    /** 所在地区。 */
    private String region;
    /** 标签（jsonb 字符串数组）。 */
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private List<String> tags;
    /** 路由位（code）最近一次修改时间（30 天限频判定用；NULL=从未改过）。列类型 TIMESTAMPTZ。 */
    private Instant codeChangedAt;
    /** 公司 Logo 文件 id（sys_file.file_id 对外字符串；NULL=底座默认 Logo）。 */
    private String logoFileId;
    /** 防截屏水印是否开启（租户级开关；NULL=默认开启）。 */
    private Boolean screenshotWatermarkEnabled;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
