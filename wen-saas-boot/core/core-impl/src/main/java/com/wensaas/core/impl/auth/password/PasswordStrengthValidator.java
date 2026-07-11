package com.wensaas.core.impl.auth.password;

/**
 * 密码强度校验（4 条字符类规则 + 强度条 4 段，与前端强度条口径一致）。
 *
 * <p>4 条字符类规则：小写 / 大写 / 数字 / 特殊符号；外加长度下限 8。
 * 通过判定：长度 ≥ 8 且至少满足 3 类字符规则。强度档由满足项总数映射。
 */
public final class PasswordStrengthValidator {

    private static final int MIN_LENGTH = 8;

    public enum Strength {
        WEAK, MEDIUM, STRONG, VERY_STRONG
    }

    public record Result(boolean passed, Strength strength) {
    }

    private PasswordStrengthValidator() {
    }

    /** 用默认长度下限 {@value #MIN_LENGTH} 校验。 */
    public static Result evaluate(CharSequence raw) {
        return evaluate(raw, MIN_LENGTH);
    }

    /**
     * 用指定长度下限校验（运营可在系统参数里调 {@code security_policy.password_min_length}）。
     *
     * @param minLength 长度下限；调用方一般传 {@link SecurityPolicySettings#passwordMinLength()}
     */
    public static Result evaluate(CharSequence raw, int minLength) {
        if (raw == null) {
            return new Result(false, Strength.WEAK);
        }
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= 'a' && c <= 'z') {
                hasLower = true;
            } else if (c >= 'A' && c <= 'Z') {
                hasUpper = true;
            } else if (c >= '0' && c <= '9') {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }
        int classes = (hasLower ? 1 : 0) + (hasUpper ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        boolean lengthOk = raw.length() >= minLength;
        boolean passed = lengthOk && classes >= 3;

        int score = (lengthOk ? 1 : 0) + classes;
        Strength strength = switch (score) {
            case 0, 1, 2 -> Strength.WEAK;
            case 3 -> Strength.MEDIUM;
            case 4 -> Strength.STRONG;
            default -> Strength.VERY_STRONG;
        };
        return new Result(passed, strength);
    }
}
