package com.wensaas.core.api.pkg;

/**
 * 套餐状态拦截异常：模块未授权（未购买）或套餐只读（已过期）。
 *
 * <p>Web 层映射：{@code PackageStatusRestExceptionAdvice}。
 * <ul>
 *   <li>{@link Code#PACKAGE_UNAUTHORIZED} → 402 Payment Required</li>
 *   <li>{@link Code#PACKAGE_READONLY} → 403 Forbidden</li>
 * </ul>
 */
public class PackageStatusException extends RuntimeException {

    public enum Code {
        /** 租户未购买该模块所属套餐，读写皆不可访问。 */
        PACKAGE_UNAUTHORIZED,
        /** 套餐已到期且未撤销，模块进入只读状态，写方法被拦截。 */
        PACKAGE_READONLY
    }

    private final Code code;
    private final String moduleCode;

    public PackageStatusException(Code code, String moduleCode, String message) {
        super(message);
        this.code = code;
        this.moduleCode = moduleCode;
    }

    public Code getCode() { return code; }

    public String getModuleCode() { return moduleCode; }
}
