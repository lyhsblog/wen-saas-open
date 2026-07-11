package com.wensaas.core.web.tenant;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldObject;
import com.wensaas.core.api.customfield.CustomFieldQuota;
import com.wensaas.core.api.customfield.CustomFieldRefObject;
import com.wensaas.core.api.customfield.CustomFieldService;
import com.wensaas.core.api.customfield.CustomFieldType;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.customfield.def.CreateFieldRequest;
import com.wensaas.core.impl.customfield.def.CustomFieldDefManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 租户后台 · 系统管理 · 字段管理。
 *
 * <p>底座统一入口：任意声明了 {@code customFieldObjects()} 的业务模块对象都在此管理字段。
 * 读走 {@link CustomFieldService}，写走内部 {@link CustomFieldDefManager}（DDL + 元数据同事务）。
 * 主管理员恒可（{@code @RequirePermission} 对 tenantOwner 放行）。
 */
@Tag(name = "tenant-custom-field", description = "租户后台 · 字段管理")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/custom-fields")
public class TenantCustomFieldController {

    private final CustomFieldService service;
    private final CustomFieldDefManager defManager;

    public TenantCustomFieldController(CustomFieldService service, CustomFieldDefManager defManager) {
        this.service = service;
        this.defManager = defManager;
    }

    @Operation(summary = "宿主对象列表", description = "所有声明了自定义字段的业务对象（含中文名）")
    @RequirePermission("custom_field.view")
    @GetMapping("/objects")
    public List<CustomFieldObject> objects() {
        return service.listObjects();
    }

    @Operation(summary = "REF 字段可关联对象列表",
            description = "内建数据实体（成员/部门）+ 已注册业务对象；不含角色 / platform-only 实体")
    @RequirePermission("custom_field.view")
    @GetMapping("/ref-objects")
    public List<CustomFieldRefObject> refObjects() {
        return service.listRefObjects();
    }

    @Operation(summary = "字段额度", description = "本租户字段 / 索引额度与已用量")
    @RequirePermission("custom_field.view")
    @GetMapping("/quota")
    public CustomFieldQuota quota() {
        return service.getQuota();
    }

    @Operation(summary = "某对象的字段列表", description = "按 displayOrder, id 升序")
    @RequirePermission("custom_field.view")
    @GetMapping
    public List<CustomFieldDef> list(
            @Parameter(description = "业务对象编码，如 crm_demo.customer") @RequestParam String objectCode) {
        return service.listFields(objectCode);
    }

    @Operation(summary = "新增字段", description = "fieldCode 必须 cf_ 前缀；ENUM 须带 refDictCode、REF 须带 refObjectCode")
    @RequirePermission("custom_field.manage")
    @PostMapping
    public Map<String, Long> create(@Valid @RequestBody CreateRequest req) {
        long tenantId = TenantContext.require();
        Long id = defManager.createField(new CreateFieldRequest(
                tenantId, req.objectCode(), req.fieldCode(), req.fieldName(),
                CustomFieldType.fromCode(req.type()), req.required(), req.indexed(),
                req.defaultValue(), req.refDictCode(), req.refObjectCode(),
                req.displayOrder() == null ? 0 : req.displayOrder()));
        return Map.of("id", id);
    }

    @Operation(summary = "改字段名", description = "仅改显示名，不影响存储（JSON key 是 fieldCode）")
    @RequirePermission("custom_field.manage")
    @PutMapping("/{id}/name")
    public void rename(@PathVariable long id, @Valid @RequestBody RenameRequest req) {
        defManager.renameField(TenantContext.require(), id, req.name());
    }

    @Operation(summary = "索引化切换", description = "创建/删除表达式索引；占用 / 释放索引额度（DDL 可能阻塞，建议低峰期）")
    @RequirePermission("custom_field.manage")
    @PutMapping("/{id}/index")
    public void toggleIndex(@PathVariable long id, @RequestBody IndexRequest req) {
        defManager.toggleIndex(TenantContext.require(), id, req.indexed());
    }

    @Operation(summary = "删除字段（软删）", description = "软删 + 释放索引；数据 JSON key 保留，90 天后定时清理")
    @RequirePermission("custom_field.manage")
    @DeleteMapping("/{id}")
    public void drop(@PathVariable long id) {
        defManager.dropField(TenantContext.require(), id);
    }

    // ============================== 请求 DTO ==============================

    public record CreateRequest(
            @NotBlank String objectCode,
            @NotBlank String fieldCode,
            @NotBlank String fieldName,
            @NotBlank String type,
            boolean required,
            boolean indexed,
            String defaultValue,
            String refDictCode,
            String refObjectCode,
            Integer displayOrder) {
    }

    public record RenameRequest(@NotBlank String name) {
    }

    public record IndexRequest(boolean indexed) {
    }
}
