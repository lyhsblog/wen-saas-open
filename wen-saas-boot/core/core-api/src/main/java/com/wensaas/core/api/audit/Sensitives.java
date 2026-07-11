package com.wensaas.core.api.audit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 脱敏工具。v1 仅扫描直接字段，不递归嵌套对象 / List / Map。
 *
 * <p>调用方：{@code AuditableAspect} 在投递 Publisher 之前对 {@code change_before} /
 * {@code change_after} 调用 {@link #maskByAnnotation(Object)}；业务方主动写日志时可调
 * 单值便捷方法（{@link #maskPhone(String)} 等）。
 */
public final class Sensitives {

    /** 反射字段缓存：每个 Class 仅扫描一次。 */
    private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    /** CUSTOM 类型脱敏 handler 表。由调用方（实现层）通过 {@link #registerHandler} 注入。 */
    private static final Map<String, SensitiveHandler> HANDLERS = new ConcurrentHashMap<>();

    private Sensitives() {
    }

    /**
     * 注册 {@link SensitiveType#CUSTOM} 用的 handler。由 core-impl 启动期收集
     * {@link SensitiveHandler} Bean 后调用。
     */
    public static void registerHandler(SensitiveHandler handler) {
        if (handler != null && handler.name() != null && !handler.name().isBlank()) {
            HANDLERS.put(handler.name(), handler);
        }
    }

    /** 测试 / 重启清理。 */
    public static void clearHandlers() {
        HANDLERS.clear();
    }

    /**
     * 浅扫描对象的直接字段，按 {@link Sensitive} 注解脱敏后返回 {@code LinkedHashMap}。
     *
     * <p>{@code null} 入参返回 null；{@code static / transient} 字段跳过；
     * 不带 {@link Sensitive} 注解的字段值原样保留（待 JSON 序列化器处理）。
     */
    public static Map<String, Object> maskByAnnotation(Object dto) {
        if (dto == null) {
            return null;
        }
        List<Field> fields = FIELD_CACHE.computeIfAbsent(dto.getClass(), Sensitives::scanFields);
        if (fields.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>(fields.size() * 2);
        for (Field f : fields) {
            try {
                Object raw = f.get(dto);
                Sensitive ann = f.getAnnotation(Sensitive.class);
                out.put(f.getName(), ann == null ? raw : applyMask(raw, ann));
            } catch (IllegalAccessException e) {
                // 设置 accessible 失败的字段跳过（不应发生，已 setAccessible）
            }
        }
        return out;
    }

    private static Object applyMask(Object raw, Sensitive ann) {
        if (raw == null) {
            return null;
        }
        return switch (ann.value()) {
            case PHONE -> maskPhone(String.valueOf(raw));
            case EMAIL -> maskEmail(String.valueOf(raw));
            case ID_CARD -> maskIdCard(String.valueOf(raw));
            case BANK_CARD -> maskBankCard(String.valueOf(raw));
            case REMOVE -> "***";
            case CUSTOM -> applyCustom(raw, ann.handler());
        };
    }

    private static Object applyCustom(Object raw, String handlerName) {
        if (handlerName == null || handlerName.isBlank()) {
            return "***";
        }
        SensitiveHandler h = HANDLERS.get(handlerName);
        return h == null ? "***" : h.mask(raw);
    }

    private static List<Field> scanFields(Class<?> type) {
        List<Field> all = new ArrayList<>();
        Class<?> c = type;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) {
                    continue;
                }
                f.setAccessible(true);
                all.add(f);
            }
            c = c.getSuperclass();
        }
        return List.copyOf(all);
    }

    // ---------------- 单值脱敏便捷方法 ----------------

    /** 手机号 11 位 → 前 3 + {@code "****"} + 后 4；长度异常返回 {@code "***"}。 */
    public static String maskPhone(String raw) {
        return maskByLength(raw, 11, s -> s.substring(0, 3) + "****" + s.substring(s.length() - 4));
    }

    /**
     * 邮箱：{@code a***e@x.com}。本地部分 < 3 字符时退化为 {@code "***@domain"}；
     * 无 @ 时返回 {@code "***"}。
     */
    public static String maskEmail(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        int at = raw.indexOf('@');
        if (at <= 0 || at == raw.length() - 1) {
            return "***";
        }
        String local = raw.substring(0, at);
        String domain = raw.substring(at);
        if (local.length() < 3) {
            return "***" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    /** 身份证 15 或 18 位 → 前 3 + 中间 * + 后 4。 */
    public static String maskIdCard(String raw) {
        if (raw == null) {
            return null;
        }
        int len = raw.length();
        if (len != 15 && len != 18) {
            return "***";
        }
        int stars = len - 7;
        return raw.substring(0, 3) + "*".repeat(stars) + raw.substring(len - 4);
    }

    /** 银行卡 ≥ 12 位 → 前 4 + {@code "****"} + 后 4。 */
    public static String maskBankCard(String raw) {
        if (raw == null || raw.length() < 12) {
            return "***";
        }
        return raw.substring(0, 4) + "****" + raw.substring(raw.length() - 4);
    }

    private static String maskByLength(String raw, int expected, Function<String, String> fn) {
        if (raw == null) {
            return null;
        }
        if (raw.length() != expected) {
            return "***";
        }
        return fn.apply(raw);
    }
}
