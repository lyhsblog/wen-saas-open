package com.wensaas.crmdemo.spi;

import com.wensaas.core.api.compliance.spi.ExportObjectContributor;
import com.wensaas.core.api.compliance.spi.ExportObjectDef;
import com.wensaas.core.api.event.CoreEventTypes;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.template.TemplateContent;
import com.wensaas.core.api.notification.template.TemplateVariableDef;
import com.wensaas.core.api.spi.BusinessModule;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.ModuleMetadata;
import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import com.wensaas.core.api.spi.def.DashboardCardDef;
import com.wensaas.core.api.spi.def.DashboardCardDisplay;
import com.wensaas.core.api.spi.def.DashboardCardSpan;
import com.wensaas.core.api.spi.def.DataPermissionTableDef;
import com.wensaas.core.api.spi.def.DictGroupDef;
import com.wensaas.core.api.spi.def.DictItemDef;
import com.wensaas.core.api.spi.def.EventSubscription;
import com.wensaas.core.api.spi.def.FieldType;
import com.wensaas.core.api.spi.def.H5PageDef;
import com.wensaas.core.api.spi.def.UniqueScope;
import com.wensaas.core.api.spi.def.ImportEntityDef;
import com.wensaas.core.api.spi.def.ImportFieldDef;
import com.wensaas.core.api.spi.def.NotificationTemplateDef;
import com.wensaas.core.api.spi.def.PermissionDef;
import com.wensaas.core.api.spi.def.ReferenceDef;
import com.wensaas.core.api.spi.def.ReferenceMatch;
import com.wensaas.core.api.spi.def.TaskHandlerDef;
import com.wensaas.crmdemo.dashboard.CrmDemoMyCustomersCardProvider;
import com.wensaas.crmdemo.dashboard.CrmDemoWeeklyNewCardProvider;
import com.wensaas.crmdemo.event.CrmDemoMemberDisabledHandler;
import com.wensaas.crmdemo.imports.CrmDemoCustomerImportTaskHandler;

import java.util.List;
import java.util.Map;

/**
 * CRM 客户管理 Demo 业务模块描述符。
 *
 * <p>这是 {@code modules/} 下第一个真实业务模块，作为底座三块能力的端到端验收载体：
 * <ul>
 *   <li>自定义字段：{@link #customFieldObjects()} 声明 {@code crm_demo.customer} 宿主对象</li>
 *   <li>数据权限：{@link #dataPermissionTables()} 声明 {@code crm_demo_customer} 按 负责人/归属部门 过滤</li>
 *   <li>列偏好：前端在客户列表接 {@code useColumnPreference}（与后端模块无耦合）</li>
 * </ul>
 *
 * <p>对象「客户」的中文 type label 不在此声明，统一以同 code 的 {@code NameLookup#displayLabel()}
 * 为权威单一来源（见 {@code CrmDemoNameSourceConfiguration}）。
 *
 * <p>端点走 {@code /api/v1/biz/crm_demo/*} 业务命名空间。{@link #apiPathPrefixes()} 声明该前缀，
 * 由 {@code PackageStatusWebFilter} 按租户套餐真拦截（未购 402、过期写 403）；
 * 非空前缀也使本模块成为套餐可选业务模块（{@code kind=BUSINESS}）。
 */
@BusinessModule
public class CrmDemoModule implements BusinessModuleDescriptor, ExportObjectContributor {

    /** 业务对象编码，与 entity {@code @CustomFieldExtension} / NameLookup entityType 三处一致。 */
    public static final String OBJECT_CUSTOMER = "crm_demo.customer";
    public static final String TABLE_CUSTOMER = "crm_demo_customer";

    /** 「客户来源」字典分组 code（{@code source} 标准字段存该分组的项 code）。 */
    public static final String DICT_CUSTOMER_SOURCE = "crm_demo.customer_source";

    /** 「客户被分配」站内信模板 code。 */
    public static final String TPL_CUSTOMER_ASSIGNED = "crm_demo.customer_assigned";

    /** 「负责人被停用，名下客户待改派」站内信模板 code。 */
    public static final String TPL_OWNER_DISABLED = "crm_demo.owner_disabled_reminder";

    /** 「阶段停留超期」站内信模板 code（CrmDemoStageStaleReminder 按负责人聚合发送）。 */
    public static final String TPL_STAGE_STALE = "crm_demo.customer_stage_stale";

    /** 导入实体 code 与任务处理器 code。 */
    public static final String IMPORT_CUSTOMER = "crm_demo.customer";
    public static final String IMPORT_CUSTOMER_HANDLER = "crm_demo.task.import.customer";

    /** 导出对象 code（与 ExportDataProvider.objectCode 按字符串对齐；带前缀防与内建/他模块碰撞）。 */
    public static final String EXPORT_CUSTOMER = "crm_demo_customer";
    /** 「我的客户」导出对象：服务端强制负责人=申请人本人（与 GET /mine 同语义），权限锚定 view。 */
    public static final String EXPORT_CUSTOMER_MINE = "crm_demo_customer_mine";

    @Override
    public ModuleMetadata metadata() {
        return new ModuleMetadata(
                "crm_demo",
                "客户管理 Demo",
                "演示自定义字段 / 列偏好 / 数据权限三能力的样板业务模块",
                "1.0.0",
                null,
                List.of());
    }

    @Override
    public List<String> apiPathPrefixes() {
        // openapi 前缀一并声明：套餐门禁对开放 API 同样生效（AppKey 鉴权 Filter 顺序在前、
        // TenantContext 已就位），调用日志 module_code 也按此归类到 crm_demo
        return List.of("/api/v1/biz/crm_demo/", "/api/v1/openapi/crm_demo/");
    }

    @Override
    public List<PermissionDef> permissions() {
        // 查看权限按视图拆两级：view=个人视图（我的客户 + 详情 + 「我的客户」导出[负责人恒为本人]），
        // view_manage=管理视图（全量列表 / 任意负责人筛选 / 全量导出）。
        // 行级数据范围统一以 view 为锚点 action 配置（读端点 @DataPermission 只写 view）——
        // 「能进哪个视图」由功能权限区分，「视图里能看到哪些行」只有一个范围旋钮，避免两个 action 各配范围后
        // 管理列表可见、详情却被更窄范围拒绝的错位。
        return List.of(
                new PermissionDef("crm_demo.customer.view", "客户 · 查看",
                        "「我的客户」视图、客户详情与「我的客户」导出（负责人恒为本人）；客户数据范围以本权限点配置"),
                new PermissionDef("crm_demo.customer.view_manage", "客户 · 管理视图",
                        "「客户管理」全量列表、按负责人筛选与全量导出；需配合「客户 · 查看」一并授予"),
                new PermissionDef("crm_demo.customer.edit", "客户 · 编辑", "新增 / 编辑 / 删除客户、推进状态")
        );
    }

    @Override
    public List<CustomFieldObjectDef> customFieldObjects() {
        return List.of(
                new CustomFieldObjectDef(OBJECT_CUSTOMER, TABLE_CUSTOMER, "extra", "客户自定义字段")
        );
    }

    @Override
    public List<DataPermissionTableDef> dataPermissionTables() {
        // 行级归属统一按「负责人」口径：SELF=owner_id（分配给我的客户，与 /mine 视图一致）、
        // DEPT=owner_dept_id（归属部门）。创建人（creator_id）不是业务归属，不做行级锚点。
        return List.of(
                new DataPermissionTableDef("crm_demo.customer", "客户",
                        TABLE_CUSTOMER, "owner_id", "owner_dept_id")
        );
    }

    @Override
    public List<DictGroupDef> dictGroups() {
        return List.of(new DictGroupDef(
                DICT_CUSTOMER_SOURCE, "客户来源",
                "客户的获客渠道；「来源」标准字段的取值范围，租户可在数据字典里增改项",
                false, List.of(),
                List.of(
                        new DictItemDef("ad", "广告投放", null, 10, null, Map.of()),
                        new DictItemDef("referral", "转介绍", null, 20, null, Map.of()),
                        new DictItemDef("self_dev", "自主开拓", null, 30, null, Map.of()),
                        new DictItemDef("expo", "展会活动", null, 40, null, Map.of()),
                        new DictItemDef("other", "其他", null, 90, null, Map.of()))));
    }

    @Override
    public List<DashboardCardDef> dashboardCards() {
        return List.of(
                new DashboardCardDef("crm_demo.my_customers", "我的客户",
                        DashboardCardDisplay.LIST, DashboardCardSpan.THIRD,
                        "/crm-demo/my-customers", List.of(), 100, true,
                        CrmDemoMyCustomersCardProvider.class),
                new DashboardCardDef("crm_demo.weekly_new", "本周新增客户",
                        DashboardCardDisplay.NUMBER_TREND, DashboardCardSpan.THIRD,
                        "/crm-demo/customers", List.of(), 110, true,
                        CrmDemoWeeklyNewCardProvider.class));
    }

    @Override
    public List<NotificationTemplateDef> notificationTemplates() {
        return List.of(
                new NotificationTemplateDef(
                        TPL_CUSTOMER_ASSIGNED, "客户分配通知",
                        "客户负责人变更为某成员时通知该成员",
                        List.of(Channel.STATION),
                        Map.of(Channel.STATION, new TemplateContent(
                                "客户分配",
                                "{operatorName} 将客户「{customerName}」分配给你，请及时跟进。")),
                        List.of(
                                new TemplateVariableDef("customerName", "示例客户", true),
                                new TemplateVariableDef("operatorName", "张三", true)),
                        Priority.NORMAL),
                new NotificationTemplateDef(
                        TPL_OWNER_DISABLED, "负责人停用改派提醒",
                        "成员被停用且名下仍有跟进中客户时，提醒主管理员与归属部门负责人改派",
                        List.of(Channel.STATION),
                        Map.of(Channel.STATION, new TemplateContent(
                                "客户待改派",
                                "成员 {memberName} 已被停用，名下还有 {count} 个跟进中的客户，请尽快改派负责人。")),
                        List.of(
                                new TemplateVariableDef("memberName", "李四", true),
                                new TemplateVariableDef("count", "3", true)),
                        Priority.IMPORTANT),
                new NotificationTemplateDef(
                        TPL_STAGE_STALE, "阶段停留超期提醒",
                        "客户在 LEAD/FOLLOWING 阶段停留超过阈值天数未推进时，按负责人聚合提醒（定时任务触发）",
                        List.of(Channel.STATION),
                        Map.of(Channel.STATION, new TemplateContent(
                                "客户跟进超期",
                                "你名下有 {count} 个客户在当前阶段停留超过 {days} 天未推进，请及时跟进。")),
                        List.of(
                                new TemplateVariableDef("count", "2", true),
                                new TemplateVariableDef("days", "7", true)),
                        Priority.NORMAL));
    }

    @Override
    public List<H5PageDef> h5Pages() {
        // 声明 H5 页面。route 须以模块 code 为前缀（NamespaceValidator 启动期校验），
        // 与 h5 app 侧 manifest（lib/modules.ts 的 h5Routes）挂载的 /{tenantKey}/crm_demo/my-customers 对齐。
        // 前端挂载由 h5 app 的 module-runtime manifest 驱动；本声明供运营端模块详情贡献计数 + 命名空间校验
        return List.of(new H5PageDef("crm_demo/my-customers", "我的客户"));
    }

    @Override
    public List<EventSubscription> eventSubscriptions() {
        // 订阅平台「成员停用」事件：名下客户改派提醒（进程内订阅派发的首个业务示例）
        return List.of(new EventSubscription(
                CoreEventTypes.MEMBER_DISABLED, CrmDemoMemberDisabledHandler.BEAN_NAME));
    }

    @Override
    public List<ImportEntityDef> importEntities() {
        // 阶段/来源接受 code 或中文双写法（校验期归一化）；负责人按手机/邮箱定位成员，空=导入发起人
        return List.of(ImportEntityDef.of(IMPORT_CUSTOMER, "客户", IMPORT_CUSTOMER_HANDLER, List.of(
                new ImportFieldDef("客户名称", "name", FieldType.STRING, true,
                        null, UniqueScope.NONE, null, "示例客户"),
                // 编号唯一性：文件内 + DB 查重都在预览期拦截（引擎按声明批查，取数走 resolver）
                new ImportFieldDef("客户编号", "code", FieldType.STRING, false,
                        null, UniqueScope.BOTH, null, "CUS-001"),
                new ImportFieldDef("阶段", "status", FieldType.STRING, false,
                        null, UniqueScope.NONE, null, "潜在"),
                new ImportFieldDef("来源", "source", FieldType.STRING, false,
                        null, UniqueScope.NONE,
                        new ReferenceDef(DICT_CUSTOMER_SOURCE, ReferenceMatch.BY_CODE, "「客户来源」字典项", null),
                        "转介绍"),
                new ImportFieldDef("负责人手机/邮箱", "owner", FieldType.STRING, false,
                        null, UniqueScope.NONE, null, "13800138000"))));
    }

    @Override
    public List<TaskHandlerDef> taskHandlers() {
        return List.of(new TaskHandlerDef(IMPORT_CUSTOMER_HANDLER, CrmDemoCustomerImportTaskHandler.class));
    }

    /** 导出对象声明（合规 SPI：默认需审批，主管理员可在合规策略页按租户覆盖）。 */
    @Override
    public List<ExportObjectDef> exportObjects() {
        return List.of(
                new ExportObjectDef(EXPORT_CUSTOMER, "客户列表（Demo）", "crm_demo", true),
                new ExportObjectDef(EXPORT_CUSTOMER_MINE, "我的客户（Demo）", "crm_demo", true));
    }
}
