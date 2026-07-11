package com.wensaas.core.impl.openapi.gateway;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * IP 白名单匹配。
 *
 * <p>支持单 IP（{@code 1.2.3.4}）+ CIDR（{@code 10.0.0.0/8}）；空白名单视为不限。
 * 仅 IPv4；IPv6 v1 暂不支持（PG JSONB 数组里写 IPv6 也会被识别为非法 CIDR）。
 */
public class IpWhitelistMatcher {

    /** 白名单为 null / 空时放行；非空则要求 clientIp 命中至少一条规则。 */
    public boolean matches(String clientIp, List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) {
            return true;
        }
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        long clientLong;
        try {
            clientLong = ipv4ToLong(clientIp);
        } catch (Exception e) {
            return false;
        }
        for (String rule : whitelist) {
            if (matchesRule(clientLong, rule)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesRule(long clientLong, String rule) {
        if (rule == null || rule.isBlank()) {
            return false;
        }
        int slash = rule.indexOf('/');
        try {
            if (slash < 0) {
                return clientLong == ipv4ToLong(rule);
            }
            String ip = rule.substring(0, slash);
            int prefix = Integer.parseInt(rule.substring(slash + 1));
            if (prefix < 0 || prefix > 32) {
                return false;
            }
            long mask = prefix == 0 ? 0L : ((-1L << (32 - prefix)) & 0xFFFFFFFFL);
            long network = ipv4ToLong(ip) & mask;
            return (clientLong & mask) == network;
        } catch (Exception e) {
            return false;
        }
    }

    private static long ipv4ToLong(String ip) throws UnknownHostException {
        byte[] addr = InetAddress.getByName(ip).getAddress();
        if (addr.length != 4) {
            throw new UnknownHostException("非 IPv4: " + ip);
        }
        return ((addr[0] & 0xFFL) << 24)
                | ((addr[1] & 0xFFL) << 16)
                | ((addr[2] & 0xFFL) << 8)
                | (addr[3] & 0xFFL);
    }
}
