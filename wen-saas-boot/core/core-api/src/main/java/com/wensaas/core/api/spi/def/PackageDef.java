package com.wensaas.core.api.spi.def;

import java.util.List;

/**
 * 预置套餐包贡献项。
 *
 * <p>启动期以 insert-if-missing 语义同步到 {@code sys_package} / {@code sys_package_module}：
 * 编码不存在才插入（默认插入即上架，开箱即用）；已存在则整行跳过——套餐落库后归运营维护
 * （改名 / 调整模块集 / 上下架 / 归档），重启不覆盖运营修改。声明的套餐不可物理删除
 * （防止重启后被重新插入而"复活"），运营侧要下线走下架 / 归档。
 *
 * @param code            套餐编码，按 {@code {module}.{pkg_name}} 命名（如 "crm_demo.starter"），全局唯一
 * @param name            显示名
 * @param description     描述（可空）
 * @param moduleCodes     额外包含的业务模块编码（允许跨模块引用打 bundle 包）；落库时自动并入
 *                        声明模块自身，空 = 仅含自身。引用不存在或未启用的模块会阻断启动
 * @param defaultTtlDays  默认有效期天数；null=永久；非 null 须 &gt; 0
 * @param publishOnCreate true=首次插入即上架（默认，开箱即用）；false=首次插入为草稿，待运营发布
 */
public record PackageDef(String code,
                          String name,
                          String description,
                          List<String> moduleCodes,
                          Integer defaultTtlDays,
                          boolean publishOnCreate) {

    public PackageDef {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("预置套餐 code 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("预置套餐 name 不能为空: " + code);
        }
        moduleCodes = moduleCodes == null ? List.of() : List.copyOf(moduleCodes);
        if (defaultTtlDays != null && defaultTtlDays <= 0) {
            throw new IllegalArgumentException("预置套餐 defaultTtlDays 须为正数或 null（永久）: " + code);
        }
    }

    /** 最常用工厂：仅含声明模块自身、永久有效、首次插入即上架。 */
    public static PackageDef of(String code, String name, String description) {
        return new PackageDef(code, name, description, List.of(), null, true);
    }
}
