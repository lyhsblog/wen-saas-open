package com.wensaas.core.impl.auth.login;

/**
 * User-Agent 轻量解析：把原始 UA 串归纳为「浏览器 · 系统」可读标签，供个人中心
 * 「登录设备 / 会话」识别设备（T8-03）。仅做关键字启发式匹配，不引第三方库；命中不到时尽量降级。
 */
public final class UserAgents {

    private UserAgents() {
    }

    /**
     * 归纳为如「Chrome · Windows」「Safari · iOS」的设备标签。
     *
     * @param ua User-Agent 原文，可能为 null
     * @return 可读标签；ua 为空时返回「未知设备」
     */
    public static String describe(String ua) {
        if (ua == null || ua.isBlank()) {
            return "未知设备";
        }
        String browser = browserOf(ua);
        String os = osOf(ua);
        if (browser == null && os == null) {
            return "未知设备";
        }
        if (browser == null) {
            return os;
        }
        if (os == null) {
            return browser;
        }
        return browser + " · " + os;
    }

    private static String browserOf(String ua) {
        // 顺序敏感：Edge/OPR/部分内核都含 "Chrome"，需先于 Chrome 判断。
        if (ua.contains("Edg/") || ua.contains("Edge")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("MicroMessenger")) {
            return "微信";
        }
        if (ua.contains("Firefox")) {
            return "Firefox";
        }
        if (ua.contains("Chrome") || ua.contains("CriOS")) {
            return "Chrome";
        }
        if (ua.contains("Safari") || ua.contains("Version/")) {
            return "Safari";
        }
        return null;
    }

    private static String osOf(String ua) {
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS")) {
            return "iOS";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        // Mac OS X 需排除 iOS（iPhone 的 UA 也含 "like Mac OS X"），故置于 iOS 之后。
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return null;
    }
}
