package com.wensaas.core.impl.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Webhook 回调地址保存期校验（防 SSRF）。
 *
 * <p>规则：仅 http(s)；host 必填；host 为 IP 字面量或可解析域名时，任一解析结果命中
 * 环回 / 私网 / 链路本地 / 任意地址 / 组播 / IPv6 ULA 即拒绝。解析失败也拒绝（地址不可达无意义）。
 *
 * <p>边界：校验发生在保存时，DNS 结果可能随后变化（TOCTOU）——本校验挡的是误配与低成本探测，
 * 纵深防御仍建议在出口网络层（安全组 / egress 策略）限制可达网段。本地联调打 localhost 接收端时
 * 用配置 {@code wen-saas-boot.webhook.allow-private-targets=true} 放行。
 */
final class WebhookUrlValidator {

    private WebhookUrlValidator() {
    }

    static void validate(String url, boolean allowPrivateTargets) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw reject("回调地址不是合法 URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw reject("回调地址仅支持 http/https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw reject("回调地址缺少主机名");
        }
        if (allowPrivateTargets) {
            return;
        }
        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw reject("回调地址主机名无法解析");
        }
        for (InetAddress addr : addrs) {
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                    || addr.isAnyLocalAddress() || addr.isMulticastAddress() || isIpv6UniqueLocal(addr)) {
                throw reject("回调地址不能指向私网/环回地址（" + addr.getHostAddress() + "）");
            }
        }
    }

    /** IPv6 ULA fc00::/7（isSiteLocalAddress 只覆盖已废弃的 fec0::/10）。 */
    private static boolean isIpv6UniqueLocal(InetAddress addr) {
        byte[] b = addr.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;
    }

    private static ResponseStatusException reject(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
