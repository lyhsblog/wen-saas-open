package com.wensaas.core.web.dept;

import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.org.DeptAdminService;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.web.dept.dto.DeptCreateRequest;
import com.wensaas.core.web.dept.dto.DeptMoveRequest;
import com.wensaas.core.web.dept.dto.DeptReorderRequest;
import com.wensaas.core.web.dept.dto.DeptUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
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
 * 部门管理（租户后台）。
 */
@Tag(name = "tenant-dept", description = "租户后台 · 组织架构")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/depts")
public class DeptAdminController {

    private final DeptAdminService service;

    public DeptAdminController(DeptAdminService service) {
        this.service = service;
    }

    @Operation(summary = "列出部门树",
            description = "返回当前租户的完整部门树（按 sort_order, name 排序），含每个部门的 memberCount")
    @RequirePermission("org.dept.list")
    @GetMapping
    public List<DeptAdminService.DeptView> listTree() {
        return service.listTree();
    }

    @Operation(summary = "取部门详情")
    @RequirePermission("org.dept.list")
    @GetMapping("/{deptId}")
    public DeptAdminService.DeptView getDetail(@PathVariable long deptId) {
        return service.getDetail(deptId);
    }

    @Operation(summary = "新建部门")
    @RequirePermission("org.dept.create")
    @Auditable(module = "__core__", action = "新建部门", objectType = EntityTypes.SYS_DEPT,
            objectIdExpr = "#result.id()", objectDescExpr = "#result.name()")
    @PostMapping
    public DeptAdminService.DeptView create(@Valid @RequestBody DeptCreateRequest req) {
        return service.create(new DeptAdminService.CreateDeptRequest(
                req.parentId(), req.name(), req.sortOrder()));
    }

    @Operation(summary = "编辑部门基本信息")
    @RequirePermission("org.dept.update")
    @Auditable(module = "__core__", action = "编辑部门", objectType = EntityTypes.SYS_DEPT,
            objectIdExpr = "#deptId", objectDescExpr = "#result.name()")
    @PutMapping("/{deptId}")
    public DeptAdminService.DeptView update(@PathVariable long deptId,
                                              @Valid @RequestBody DeptUpdateRequest req) {
        return service.update(deptId, new DeptAdminService.UpdateDeptRequest(req.name(), req.sortOrder()));
    }

    @Operation(summary = "删除部门",
            description = "要求部门下无子部门且无成员（含兼任）")
    @RequirePermission("org.dept.delete")
    @Auditable(module = "__core__", action = "删除部门", objectType = EntityTypes.SYS_DEPT,
            objectIdExpr = "#deptId")
    @DeleteMapping("/{deptId}")
    public ResponseEntity<Void> delete(@PathVariable long deptId) {
        service.delete(deptId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "移动部门到新上级",
            description = "子树 ancestors 路径会自动批量重写；不能移到自身或子孙下；总深度上限 5")
    @RequirePermission("org.dept.update")
    @Auditable(module = "__core__", action = "移动", objectType = EntityTypes.SYS_DEPT,
            objectIdExpr = "#deptId", objectDescExpr = "#result.name()")
    @PutMapping("/{deptId}/parent")
    public DeptAdminService.DeptView move(@PathVariable long deptId,
                                            @Valid @RequestBody DeptMoveRequest req) {
        return service.move(deptId, req.newParentId());
    }

    @Operation(summary = "同级批量重排",
            description = "orderedIds 为该父级下全部子部门按新顺序的 id；单事务整体重写 sort_order")
    @RequirePermission("org.dept.update")
    @Auditable(module = "__core__", action = "调整部门排序", objectType = EntityTypes.SYS_DEPT)
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(@Valid @RequestBody DeptReorderRequest req) {
        service.reorder(req.parentId() == null ? 0L : req.parentId(), req.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "启用 / 停用部门")
    @RequirePermission("org.dept.update")
    @Auditable(module = "__core__", action = "切换部门状态", objectType = EntityTypes.SYS_DEPT,
            objectIdExpr = "#deptId", objectDescExpr = "#result.name()")
    @PutMapping("/{deptId}/status")
    public DeptAdminService.DeptView setStatus(@PathVariable long deptId,
                                                 @RequestParam boolean enabled) {
        return service.setStatus(deptId, enabled);
    }

    @Operation(summary = "列出部门负责人")
    @RequirePermission("org.dept.list")
    @GetMapping("/{deptId}/leaders")
    public List<DeptAdminService.DeptLeaderRef> listLeaders(@PathVariable long deptId) {
        return service.listLeaders(deptId);
    }

    @Operation(summary = "设置部门负责人（全量替换）",
            description = "userIds 须为该部门成员（主或兼）；传空数组 = 清空负责人")
    @RequirePermission("org.dept.set_leader")
    @Auditable(module = "__core__", action = "设置负责人", objectType = EntityTypes.SYS_DEPT,
            objectIdExpr = "#deptId")
    @PutMapping("/{deptId}/leaders")
    public List<DeptAdminService.DeptLeaderRef> setLeaders(@PathVariable long deptId,
                                                           @RequestBody DeptLeadersRequest req) {
        return service.setLeaders(deptId, req.userIds());
    }

    /** 设置部门负责人请求体。 */
    public record DeptLeadersRequest(List<Long> userIds) {
    }
}
