package com.wensaas.crmdemo.entity;

import java.util.Set;

/**
 * 客户阶段状态机（标准字段，非自定义）。
 *
 * <pre>
 * LEAD(潜在) → FOLLOWING(跟进中) → WON(成交)
 *                              ↘ LOST(流失)        ← 终态
 *           ↘ LOST(流失)
 * </pre>
 *
 * <p>WON / LOST 为终态，不可再流转。
 */
public enum CustomerStatus {

    LEAD("潜在", Set.of("FOLLOWING", "LOST")),
    FOLLOWING("跟进中", Set.of("WON", "LOST")),
    WON("成交", Set.of()),
    LOST("流失", Set.of());

    private final String label;
    private final Set<String> allowedNext;

    CustomerStatus(String label, Set<String> allowedNext) {
        this.label = label;
        this.allowedNext = allowedNext;
    }

    public String label() {
        return label;
    }

    /** 是否允许从当前状态流转到 {@code target}。 */
    public boolean canTransitionTo(CustomerStatus target) {
        return target != null && allowedNext.contains(target.name());
    }

    /** 宽松解析，非法值返回 null。 */
    public static CustomerStatus parse(String s) {
        if (s == null) {
            return null;
        }
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 更宽松解析：接受枚举名（不分大小写）或中文 label（导入场景双写法），非法返回 null。 */
    public static CustomerStatus parseFlexible(String token) {
        CustomerStatus byName = parse(token);
        if (byName != null) {
            return byName;
        }
        if (token == null) {
            return null;
        }
        String t = token.trim();
        for (CustomerStatus s : values()) {
            if (s.label.equals(t)) {
                return s;
            }
        }
        return null;
    }
}
