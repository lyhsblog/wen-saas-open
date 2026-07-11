package com.wensaas.core.impl.security;

/**
 * {@link SensitiveFieldCipher} 静态持有者。
 *
 * <p>MyBatis 的 {@code TypeHandler} 由 MyBatis 自行实例化（非 Spring Bean），无法构造注入；
 * 故沿用 {@code Sensitives.registerHandler} 同款静态桥：{@link SensitiveFieldCipher} 在
 * {@code @PostConstruct} 时 {@link #set} 自身，{@link EncryptedStringTypeHandler} 运行期 {@link #get}。
 *
 * <p>未就绪（启动早期 / 无密钥环境）时 {@link #get} 返回 {@code null}，TypeHandler 退化为
 * 明文直存直读，保证不抛异常。
 */
public final class SensitiveFieldCipherHolder {

    private static volatile SensitiveFieldCipher instance;

    private SensitiveFieldCipherHolder() {
    }

    public static void set(SensitiveFieldCipher cipher) {
        instance = cipher;
    }

    public static SensitiveFieldCipher get() {
        return instance;
    }

    /** 测试清理。 */
    public static void clear() {
        instance = null;
    }
}
