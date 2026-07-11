package com.wensaas.core.api.ops;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 运营后台 · 业务模块管理（只读内省）。
 *
 * <p>数据来源拼装：{@code sys_business_module} 表（注册时间戳）+ 内存 SPI Registry
 * （贡献能力计数、依赖、API 前缀）+ {@code sys_package_module}（关联套餐反查）。
 *
 * <p>只读内省：仅清单 + 详情，不含上线/下线/版本回滚等写操作——模块无上下线概念，注册即生效。
 */
public interface OpsBusinessModuleService {

    /** 已注册业务模块清单（按 code 升序）。 */
    List<ModuleListItem> listModules();

    /**
     * 业务模块详情（基本信息 + 贡献能力计数 + 依赖 + API 前缀 + 关联套餐）。
     *
     * @throws OpsBusinessModuleException MODULE_NOT_FOUND
     */
    ModuleDetail getModule(String code);

    // ===================== records =====================

    /** 列表项（精简视图）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ModuleListItem {
        private String code;
        private String name;
        private String version;
        private String icon;
        private Instant registeredAt;
        private int dependencyCount;
        private int contributionCount;
    }

    /** 详情。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ModuleDetail {
        private String code;
        private String name;
        private String description;
        private String version;
        private String icon;
        private Instant registeredAt;
        private List<String> dependencies;
        private List<String> apiPrefixes;
        private ModuleContribution contribution;
        private List<RelatedPackage> relatedPackages;
    }

    /** 贡献的扩展点计数（详情页 Tab「贡献的能力」）。菜单不在 SPI 声明（sys_menu 为唯一权威）。 */
    record ModuleContribution(
            int permissions,
            int dictGroups,
            int dashboardCards,
            int h5Pages,
            int notificationTemplates
    ) {}

    /** 关联套餐项（包含该模块的套餐）。{@code status}=DRAFT/ACTIVE/INACTIVE/ARCHIVED。 */
    record RelatedPackage(
            String code,
            String name,
            String status
    ) {}
}
