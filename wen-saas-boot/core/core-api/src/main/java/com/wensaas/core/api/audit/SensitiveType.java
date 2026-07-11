package com.wensaas.core.api.audit;

/**
 * 脱敏字段类型。{@link Sensitives#maskByAnnotation(Object)} 根据该类型选择脱敏函数。
 *
 * <p>v1 内置六种；新增请走 {@link #CUSTOM} + {@link SensitiveHandler} SPI。
 */
public enum SensitiveType {
    /** 手机号：{@code 13812345678 → 138****5678}。 */
    PHONE,
    /** 邮箱：{@code alice@x.com → a***e@x.com}。 */
    EMAIL,
    /** 身份证：{@code 310101200001010011 → 310***********0011}。 */
    ID_CARD,
    /** 银行卡：留前 4 后 4。 */
    BANK_CARD,
    /** 完全替换为 {@code ***}（密码 / Token / Secret 类，不应进入日志）。 */
    REMOVE,
    /** 调用注册的 {@link SensitiveHandler} SPI；通过 {@link Sensitive#handler()} 指定 bean name。 */
    CUSTOM
}
