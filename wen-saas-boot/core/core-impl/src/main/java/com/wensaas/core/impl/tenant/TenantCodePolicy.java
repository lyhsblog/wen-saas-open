package com.wensaas.core.impl.tenant;

import java.util.regex.Pattern;

/**
 * 租户 code（tenantCode）合法性策略：运营开通与租户自助改子域名共用同一套格式规则。
 *
 * <p>租户 code 会作为子域名 label（{@code {code}.saas.xuwen.site}），故须是合法 DNS label：
 * 纯小写字母/数字/连字符、字母或数字开头、不以连字符结尾，长度 5-16 位。
 *
 * <p>5-16 的长度下限天然规避平台端/基础设施短子域名前缀（{@code ops}/{@code m}/{@code www}/
 * {@code api}/{@code mail} 等均 &lt;5 位），故不再维护保留字黑名单；将来若新增 ≥5 位的平台端
 * 子域名，再在此按需补一层校验。
 */
public final class TenantCodePolicy {

    private TenantCodePolicy() {
    }

    /**
     * DNS label 格式：首尾为小写字母或数字、中间可含连字符，长度 5-16 位。
     * 首字符 + 末字符各占 1 位保证不以连字符起止；中间 3-14 位使总长为 5-16。
     */
    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9-]{3,14}[a-z0-9]$");

    /** 格式是否合法（合法 DNS label、长度 5-16 位）。 */
    public static boolean isValidFormat(String code) {
        return code != null && FORMAT.matcher(code).matches();
    }
}
