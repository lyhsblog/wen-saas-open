package com.wensaas.crmdemo.controller;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.DataPermission;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.web.common.PageQuery;
import com.wensaas.core.web.customfield.CustomFieldEquals;
import com.wensaas.crmdemo.service.CrmDemoCustomerService;
import com.wensaas.crmdemo.spi.CrmDemoModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 租户后台 · 客户管理 Demo。
 *
 * <p>「我的客户」（{@code GET /mine}，权限 view）与「客户管理」（{@code GET /}，权限 view_manage）
 * 是两个独立授权的视图端点：功能权限区分能进哪个视图；行级数据范围以 view 为锚点 action
 * 配置，由数据权限拦截器按角色配置自动叠加（/mine 不挂——负责人恒为本人已是业务语义保证，
 * 见方法注释）。
 */
@Tag(name = "tenant-crm-demo-customer", description = "租户后台 · 客户管理 Demo")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/biz/crm_demo/customers")
public class TenantCrmDemoCustomerController {

    private final CrmDemoCustomerService service;

    public TenantCrmDemoCustomerController(CrmDemoCustomerService service) {
        this.service = service;
    }

    @Operation(summary = "客户管理列表",
            description = "管理视图：分页 + 状态/关键字/任意负责人筛选 + cf_* 索引化自定义字段等值筛选。"
                    + "数据范围由角色数据权限自动叠加。")
    @DataPermission("crm_demo.customer.view")
    @RequirePermission("crm_demo.customer.view_manage")
    @GetMapping
    public CrmDemoCustomerService.PageResult list(
            @Parameter(description = "状态 LEAD/FOLLOWING/WON/LOST") @RequestParam(required = false) String status,
            @Parameter(description = "名称/编号关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "指定负责人 user_id") @RequestParam(required = false) Long ownerId,
            @Parameter(description = "客户来源（字典项 code）") @RequestParam(required = false) String source,
            @Valid @ParameterObject PageQuery pageQuery,
            @CustomFieldEquals Map<String, Object> cfEquals) {

        return service.page(new CrmDemoCustomerService.Query(
                status, keyword, ownerId, source, cfEquals, pageQuery.page(), pageQuery.size()));
    }

    @Operation(summary = "我的客户列表",
            description = "个人视图：负责人恒为当前用户，其余筛选与管理列表一致。")
    // 不挂 @DataPermission：owner_id=self 已把行集合限定为「分配给我的」，即行级最窄档（SELF）本身，
    // 任何角色范围叠加都不会更窄——省一次范围解析与 SQL 改写开销
    @RequirePermission("crm_demo.customer.view")
    @GetMapping("/mine")
    public CrmDemoCustomerService.PageResult listMine(
            @Parameter(description = "状态 LEAD/FOLLOWING/WON/LOST") @RequestParam(required = false) String status,
            @Parameter(description = "名称/编号关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "客户来源（字典项 code）") @RequestParam(required = false) String source,
            @Valid @ParameterObject PageQuery pageQuery,
            @CustomFieldEquals Map<String, Object> cfEquals) {

        Long self = UserContext.require().userId();
        return service.page(new CrmDemoCustomerService.Query(
                status, keyword, self, source, cfEquals, pageQuery.page(), pageQuery.size()));
    }

    @Operation(summary = "客户详情", description = "含标准字段 + 自定义字段视图（enum/ref 带 __label）")
    @DataPermission("crm_demo.customer.view")
    @RequirePermission("crm_demo.customer.view")
    @GetMapping("/{id}")
    public CrmDemoCustomerService.Detail get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "新增客户")
    @RequirePermission("crm_demo.customer.edit")
    @Auditable(module = "crm_demo", action = "新建客户", objectType = CrmDemoModule.OBJECT_CUSTOMER,
            objectIdExpr = "#result.id()", objectDescExpr = "#result.name()")
    @PostMapping
    public CrmDemoCustomerService.Brief create(@Valid @RequestBody CreateRequest req) {
        return service.create(new CrmDemoCustomerService.CreateCommand(
                req.name(), req.code(), req.status(), req.source(), req.ownerId(), req.ownerDeptId(), req.custom()));
    }

    @Operation(summary = "编辑客户", description = "状态变更走 /transition，不在此接口")
    @RequirePermission("crm_demo.customer.edit")
    @Auditable(module = "crm_demo", action = "编辑客户", objectType = CrmDemoModule.OBJECT_CUSTOMER,
            objectIdExpr = "#id", objectDescExpr = "#result.name()")
    @PutMapping("/{id}")
    public CrmDemoCustomerService.Brief update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return service.update(id, new CrmDemoCustomerService.UpdateCommand(
                req.name(), req.code(), req.source(), req.ownerId(), req.ownerDeptId(), req.custom()));
    }

    @Operation(summary = "删除客户（软删）")
    @RequirePermission("crm_demo.customer.edit")
    @Auditable(module = "crm_demo", action = "删除客户", objectType = CrmDemoModule.OBJECT_CUSTOMER,
            objectIdExpr = "#id", objectDescExpr = "#result.name()")
    @DeleteMapping("/{id}")
    public CrmDemoCustomerService.Brief delete(@PathVariable Long id) {
        return service.delete(id);
    }

    @Operation(summary = "推进客户状态", description = "按状态机校验流转合法性：LEAD→FOLLOWING→WON/LOST")
    @RequirePermission("crm_demo.customer.edit")
    @Auditable(module = "crm_demo", action = "推进客户阶段", objectType = CrmDemoModule.OBJECT_CUSTOMER,
            objectIdExpr = "#id", objectDescExpr = "#result.name()")
    @PostMapping("/{id}/transition")
    public CrmDemoCustomerService.Brief transition(@PathVariable Long id,
                           @Parameter(description = "目标状态") @RequestParam String target) {
        return service.transition(id, target);
    }

    // ============================== 请求 DTO ==============================

    public record CreateRequest(
            @NotBlank(message = "客户名称必填") String name,
            String code,
            String status,
            String source,
            Long ownerId,
            Long ownerDeptId,
            Map<String, Object> custom) {
    }

    public record UpdateRequest(
            @NotBlank(message = "客户名称必填") String name,
            String code,
            String source,
            Long ownerId,
            Long ownerDeptId,
            Map<String, Object> custom) {
    }
}
