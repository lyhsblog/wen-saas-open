package com.wensaas.core.web.dict;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.dict.DictAdminService;
import com.wensaas.core.api.dict.DictGroup;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.web.dict.dto.DictGroupCreateRequest;
import com.wensaas.core.web.dict.dto.DictGroupUpdateRequest;
import com.wensaas.core.web.dict.dto.DictItemCreateRequest;
import com.wensaas.core.web.dict.dto.DictItemUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

/**
 * 字典管理（租户后台）。
 *
 * <p>路径前缀 {@code /api/v1/base/dict}。
 * 所有端点要求 bearer-jwt 已认证；权限点 {@code dict.*}。
 */
@Tag(name = "tenant-dict", description = "租户后台 · 字典管理")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/dict")
public class DictAdminController {

    private final DictAdminService service;

    public DictAdminController(DictAdminService service) {
        this.service = service;
    }

    // ===================== 分组 =====================

    @Operation(summary = "列出当前租户视角下的所有分组",
            description = "合并 SYSTEM/MODULE（tenant_id=0）+ CUSTOM（当前租户），按 source asc + code asc 排序")
    @RequirePermission("dict.group.list")
    @GetMapping("/groups")
    public List<DictGroup> listGroups() {
        return service.listGroups();
    }

    @Operation(summary = "新建自定义分组")
    @RequirePermission("dict.group.create")
    @Auditable(module = "dict", action = "新建分组", objectType = "",
            objectIdExpr = "#result.code()")
    @PostMapping("/groups")
    public DictGroup createGroup(@Valid @RequestBody DictGroupCreateRequest req) {
        return service.createGroup(new DictAdminService.CreateGroupRequest(
                req.code(), req.name(), req.description(), req.supportHierarchy(), req.extraSchema()));
    }

    @Operation(summary = "编辑分组基本信息")
    @RequirePermission("dict.group.update")
    @Auditable(module = "dict", action = "更新分组", objectType = "",
            objectIdExpr = "#groupCode")
    @PutMapping("/groups/{groupCode}")
    public DictGroup updateGroup(@PathVariable String groupCode,
                                  @Valid @RequestBody DictGroupUpdateRequest req) {
        return service.updateGroup(groupCode,
                new DictAdminService.UpdateGroupRequest(req.name(), req.description(), req.extraSchema()));
    }

    @Operation(summary = "删除自定义分组（无项才可删）")
    @RequirePermission("dict.group.delete")
    @Auditable(module = "dict", action = "删除分组", objectType = "",
            objectIdExpr = "#groupCode")
    @DeleteMapping("/groups/{groupCode}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String groupCode) {
        service.deleteGroup(groupCode);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "启用 / 停用分组")
    @RequirePermission("dict.group.update")
    @Auditable(module = "dict", action = "切换分组状态", objectType = "",
            objectIdExpr = "#groupCode", objectDescExpr = "#result.name()")
    @PutMapping("/groups/{groupCode}/status")
    public DictGroup setGroupStatus(@PathVariable String groupCode,
                                     @Parameter(description = "true 启用 / false 停用")
                                     @RequestParam boolean enabled) {
        return service.setGroupStatus(groupCode, enabled);
    }

    // ===================== 项 =====================

    @Operation(summary = "列出分组下所有项",
            description = "按 supportHierarchy 决定扁平或树形返回；includeDisabled=true 时包含停用项")
    @RequirePermission("dict.item.list")
    @GetMapping("/groups/{groupCode}/items")
    public List<DictItem> listItems(@PathVariable String groupCode,
                                     @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return service.listItemsForAdmin(groupCode, includeDisabled);
    }

    @Operation(summary = "新增字典项")
    @RequirePermission("dict.item.create")
    @Auditable(module = "dict", action = "新建字典项", objectType = "",
            objectIdExpr = "#result.code()")
    @PostMapping("/groups/{groupCode}/items")
    public DictItem createItem(@PathVariable String groupCode,
                                @Valid @RequestBody DictItemCreateRequest req) {
        return service.createItem(groupCode, new DictAdminService.CreateItemRequest(
                req.code(), req.name(), req.parentId(), req.sortOrder(), req.remark(), req.extra()));
    }

    @Operation(summary = "编辑字典项")
    @RequirePermission("dict.item.update")
    @Auditable(module = "dict", action = "更新字典项", objectType = "",
            objectIdExpr = "#itemId")
    @PutMapping("/items/{itemId}")
    public DictItem updateItem(@PathVariable long itemId,
                                @Valid @RequestBody DictItemUpdateRequest req) {
        return service.updateItem(itemId, new DictAdminService.UpdateItemRequest(
                req.name(), req.sortOrder(), req.remark(), req.extra()));
    }

    @Operation(summary = "删除自定义字典项")
    @RequirePermission("dict.item.delete")
    @Auditable(module = "dict", action = "删除字典项", objectType = "",
            objectIdExpr = "#itemId")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable long itemId) {
        service.deleteItem(itemId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "启用 / 停用字典项")
    @RequirePermission("dict.item.update")
    @Auditable(module = "dict", action = "切换字典项状态", objectType = "",
            objectIdExpr = "#itemId", objectDescExpr = "#result.name()")
    @PutMapping("/items/{itemId}/status")
    public DictItem setItemStatus(@PathVariable long itemId,
                                   @RequestParam boolean enabled) {
        return service.setItemStatus(itemId, enabled);
    }
}
