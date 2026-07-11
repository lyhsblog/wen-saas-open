package com.wensaas.core.api.compliance;

/**
 * 数据隐私与合规领域异常。Web 层 {@code ComplianceRestExceptionAdvice} 映射为 ProblemDetail。
 */
public class ComplianceException extends RuntimeException {

    public enum Code {
        /** 导出申请不存在 / 不在当前可见范围。 */
        REQUEST_NOT_FOUND,
        /** 无权执行该操作（如非授权审批人 / 非主管理员）。 */
        FORBIDDEN,
        /** 当前状态不允许该操作（如审批已决策的申请）。 */
        ILLEGAL_STATE,
        /** 该导出对象无需审批，调用方应直接导出而非发起申请。 */
        EXPORT_NOT_REQUIRED
    }

    private final Code code;

    public ComplianceException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
