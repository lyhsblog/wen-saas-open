package com.wensaas.core.api.pkg;

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
 * 运营后台 · 套餐与功能管理。
 *
 * <p>覆盖：套餐包 CRUD + 4 态状态流转（草稿/上架/下架/归档）+ 套餐 ↔ 业务模块勾选。
 *
 * <p>基础包（{@code isSystem=true}）保护规则：
 * <ul>
 *   <li>仅可改 name / description，其它字段（code / status / defaultTtlDays / moduleCodes）不可改</li>
 *   <li>不可下架、不可归档、不可删除</li>
 * </ul>
 *
 * <p>上架（{@code active}）后的编辑限制（保护已勾选客户）：
 * <ul>
 *   <li>禁止移除已包含的模块（{@code MODULE_REMOVE_FORBIDDEN}）</li>
 *   <li>允许加模块、改名、改描述、改 defaultTtlDays</li>
 *   <li>code 永远不可改</li>
 * </ul>
 *
 * <p>业务模块声明的预置套餐（{@code sourceModule} 非空，启动期 SPI 同步落库）：除物理删除外
 * 与普通套餐同权——可改名/调模块集/上下架/归档；不可删除（{@code DECLARED_PACKAGE_PROTECTED}，
 * 否则重启会被同步器重新插入而"复活"）。复制出的新套餐视为运营创建（{@code sourceModule=null}）。
 *
 * <p>所涉表均在 {@code platform-only-tables} 白名单，无需 {@code @CrossTenantQuery}。
 */
public interface PackageAdminService {

    /** 列表（按状态筛选 + 关键字过滤；默认 status=ALL，按 status asc + created_at desc 排序）。 */
    List<PackageListItem> listPackages(StatusFilter status, String keyword);

    /** 详情（含 moduleCodes）。 */
    PackageDetail getPackage(String code);

    /** 可入套餐的业务模块清单（{@code sys_business_module.status=2 已上线}），按 code 排序。 */
    List<BusinessModuleItem> listAvailableModules();

    /**
     * 创建套餐包。
     *
     * @param cmd 含 code/name/description/defaultTtlDays/moduleCodes/publishImmediately
     * @return 新创建的详情
     * @throws PackageAdminException CODE_CONFLICT / MODULE_NOT_FOUND
     */
    PackageDetail createPackage(CreatePackageCommand cmd);

    /**
     * 编辑套餐包。{@code moduleCodes=null} 时不改模块勾选；非 null 全量替换。
     *
     * @throws PackageAdminException PACKAGE_NOT_FOUND / BASIC_PACKAGE_PROTECTED / MODULE_REMOVE_FORBIDDEN / MODULE_NOT_FOUND
     */
    PackageDetail updatePackage(String code, UpdatePackageCommand cmd);

    /**
     * 上架（draft / inactive → active）。
     *
     * @throws PackageAdminException PACKAGE_NOT_FOUND / INVALID_STATUS_TRANSITION（archived 不能再上架）
     */
    void publish(String code);

    /**
     * 下架（active → inactive）。
     *
     * @throws PackageAdminException PACKAGE_NOT_FOUND / BASIC_PACKAGE_PROTECTED / INVALID_STATUS_TRANSITION
     */
    void unpublish(String code);

    /**
     * 归档（inactive → archived，不可恢复）。
     *
     * @throws PackageAdminException PACKAGE_NOT_FOUND / BASIC_PACKAGE_PROTECTED / INVALID_STATUS_TRANSITION
     */
    void archive(String code);

    /**
     * 删除（仅 draft 可删，基础包与模块预置套餐不可删）。
     *
     * @throws PackageAdminException PACKAGE_NOT_FOUND / BASIC_PACKAGE_PROTECTED / DECLARED_PACKAGE_PROTECTED / INVALID_STATUS_TRANSITION
     */
    void deletePackage(String code);

    /**
     * 复制套餐（以源套餐的 description/defaultTtlDays/moduleCodes 为初值，重填 code/name）。
     * 新套餐恒为 {@code DRAFT}（草稿），不直接上架。
     *
     * @param sourceCode 源套餐编码
     * @param cmd        新套餐的 code（全局唯一，{@code ^[A-Z][A-Z0-9_]{1,63}$}）+ name
     * @throws PackageAdminException PACKAGE_NOT_FOUND（源不存在）/ CODE_CONFLICT（新编码已存在）
     */
    PackageDetail copyPackage(String sourceCode, CopyPackageCommand cmd);

    // ===================== records & enums =====================

    /** 列表筛选枚举。{@code ALL} = 不筛选（默认）。 */
    enum StatusFilter { DRAFT, ACTIVE, INACTIVE, ARCHIVED, ALL }

    /**
     * 套餐状态（对外口径统一大写；整型 {@code code()} 对齐 {@code sys_package.status} 落库值）。
     */
    enum PackageStatus {
        DRAFT(0), ACTIVE(1), ARCHIVED(2), INACTIVE(3);

        private final int code;

        PackageStatus(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        /** 由落库整型还原枚举；未知值抛 {@link IllegalArgumentException}。 */
        public static PackageStatus fromCode(int code) {
            for (PackageStatus s : values()) {
                if (s.code == code) {
                    return s;
                }
            }
            throw new IllegalArgumentException("未知套餐状态码: " + code);
        }
    }

    /** 列表项（精简视图）。{@code status}=DRAFT/ACTIVE/INACTIVE/ARCHIVED。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class PackageListItem {
        private String code;
        private String name;
        private String description;
        private PackageStatus status;
        private Integer defaultTtlDays;
        private Boolean isSystem;
        /** 来源业务模块编码（SPI 声明的预置套餐）；null=运营手工创建 */
        private String sourceModule;
        private int moduleCount;
        private Instant createdAt;
        private Instant updatedAt;
    }

    /**
     * 详情。
     *
     * @param tenantCount 当前持有（未撤销）该套餐的租户数（OPS-P-011 反查，新建/复制返回的草稿包恒 0）
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class PackageDetail {
        private String code;
        private String name;
        private String description;
        private PackageStatus status;
        private Integer defaultTtlDays;
        private Boolean isSystem;
        /** 来源业务模块编码（SPI 声明的预置套餐）；null=运营手工创建 */
        private String sourceModule;
        private List<String> moduleCodes;
        private int tenantCount;
        private Instant createdAt;
        private Instant updatedAt;
    }

    /** 业务模块项（供新建/编辑勾选）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class BusinessModuleItem {
        private String code;
        private String name;
        private String description;
        private String icon;
    }

    /**
     * 创建命令。
     *
     * @param code                全局唯一编码；非空，{@code ^[A-Z][A-Z0-9_]{1,63}$}
     * @param name                名称；非空
     * @param description         描述；可空
     * @param defaultTtlDays      默认有效期天数；null=永久
     * @param moduleCodes         勾选的业务模块编码（基础包此字段忽略）；null/空集合=不绑模块
     * @param publishImmediately  true=直接上架（status=active）；false=保存为草稿（status=draft）
     */
    record CreatePackageCommand(
            String code,
            String name,
            String description,
            Integer defaultTtlDays,
            List<String> moduleCodes,
            boolean publishImmediately
    ) {}

    /**
     * 编辑命令。null 字段表示不改。
     *
     * @param name           可空（不改）
     * @param description    可空（不改）
     * @param defaultTtlDays 可空（不改）
     * @param moduleCodes    可空：null=不改；非 null（含空集合）=全量替换
     */
    record UpdatePackageCommand(
            String name,
            String description,
            Integer defaultTtlDays,
            List<String> moduleCodes
    ) {}

    /**
     * 复制命令。
     *
     * @param code 新套餐编码；非空，{@code ^[A-Z][A-Z0-9_]{1,63}$}
     * @param name 新套餐名称；非空
     */
    record CopyPackageCommand(
            String code,
            String name
    ) {}
}
