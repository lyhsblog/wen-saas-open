package com.wensaas.core.api.auth.user;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成员导出读（auth domain 读服务，{@code UserQueryService} 之外按 ISP 单列的窄接口）。
 *
 * <p>单一消费者（合规全量导出）；含明文 mobile/email，由消费方按 {@code DataExportMode}
 * 自行掩码 / 输出明文。单列窄接口避免把导出专用读挂在通用查询面上。由 {@code UserQueryServiceImpl} 实现。
 */
public interface MemberExportQuery {

    /**
     * 当前租户全部成员的导出档（按 id 升序，**含明文 mobile/email**）。明文仅限已门控 + 审计的导出流程，
     * 禁止用于展示。受调用方租户上下文 / MP TenantLine 约束。
     */
    List<MemberExportRecord> exportableMembers();

    /** 成员导出档（明文 mobile/email，由合规导出按 mode 掩码）。 */
    record MemberExportRecord(Long id, String username, String realName, String mobile, String email,
                              String empNo, Integer status, LocalDateTime createdAt) {}
}
