package com.wensaas.core.web.tenant;

import com.wensaas.core.api.namesource.NameSearch.NameHit;
import com.wensaas.core.impl.namesource.EntitySearchService;
import com.wensaas.core.impl.namesource.EntitySearchService.EntityTypeMeta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户后台 · 实体反向搜索（通用端点）。
 *
 * <p>给「按名称/编码选实例」类场景提供两件事：可搜索实体类型清单（喂「对象类型」下拉 + 「对象」选择弹窗左侧
 * 类型列表）、在某类型内按 name 前缀 / code 精确搜候选实例（选中取 id）。**不是审计专用端点**——已知消费者：
 * 审计「对象」筛选、自定义字段 ref 选择器（{@code EntityRefPicker}）、业务页面任意「选人 / 选部门 / 选关联
 * 实例」类筛选或表单字段（如 CRM 客户demo 的「归属人」筛选，直接传 {@code type=sys.user}，不必另起端点）。
 * 新增消费者按需直接复用，无需为某个业务场景再开一个语义相同的端点。
 *
 * <p>门控：{@code /types} 与 {@code /search} 均**放开给任意登录用户**（不挂 {@code audit.view} 或其它业务权限）。
 * 原因：本端点只返回「id + 展示名」，不暴露业务字段，多个无关消费者共用同一端点；若按某个消费者的业务权限
 * 门控（如 {@code audit.view} 或 {@code org.member.list}），会让其它消费者的普通用户用不了它们自己页面里的
 * 选择器。实例名可见性的边界仍由 {@link EntitySearchService} 兜底：
 * 租户隔离 + platform-only 防越权（{@code tenantSearchable=false} 的类型不列出、也拒绝搜索），按既定设计
 * 实体搜索不再叠加角色数据范围过滤。
 */
@Tag(name = "tenant-entity-lookup", description = "租户实体反向搜索（通用：审计对象筛选 / 自定义字段 ref 选择器 / 业务页面选人选部门等筛选共用）")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/entity-lookup")
public class TenantEntityLookupController {

    private final EntitySearchService searchService;

    public TenantEntityLookupController(EntitySearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "可搜索实体类型列表（code / label / 可搜字段 fields）")
    @GetMapping("/types")
    public List<EntityTypeMeta> types() {
        return searchService.tenantSearchableTypes();
    }

    @Operation(summary = "在某类型内按单关键字搜候选实例（OR 命中各可搜字段，≤20 条，offset 滚动，无总数；登录即可）")
    @GetMapping("/{type}/search")
    public List<NameHit> search(
            @PathVariable("type") String type,
            @Parameter(description = "关键字（OR 命中各可搜字段，名称类前缀、编码类精确）")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "滚动加载偏移（省 count 开销）")
            @RequestParam(value = "offset", required = false, defaultValue = "0") int offset) {
        return searchService.tenantSearch(type, keyword, 20, offset);
    }
}
