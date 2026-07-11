package com.wensaas.core.api.exports;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 业务数据导出服务。运行在租户上下文。
 *
 * <p>发起导出时按合规策略（导出对象审批开关）分流：
 * <ul>
 *   <li>无需审批 → 直接投任务中心 EXPORT 任务（异步生成 xlsx，完成后任务中心下载）；</li>
 *   <li>需审批 → 落 {@code sys_export_request} PENDING 并通知审批人；审批通过后以
 *       <b>申请人</b>身份投递同一执行任务（任务中心按 creator 隔离，申请人可见可下载）。</li>
 * </ul>
 */
public interface ExportService {

    /**
     * 当前用户可见的导出对象（已注册 {@code ExportDataProvider} 且持发起权限），
     * 含生效审批开关——前端据此提示「将提交审批」。
     */
    List<ExportObjectOption> listObjects();

    /**
     * 发起导出。
     *
     * @throws ExportException OBJECT_UNKNOWN / FORBIDDEN / REASON_REQUIRED
     */
    SubmitExportResult submit(SubmitExportCommand cmd);

    /**
     * 发起导出命令。
     *
     * @param objectCode    导出对象 code（必填）
     * @param filters       筛选条件（provider 自定义键；随任务/台账快照透传）
     * @param filterSummary 筛选条件人类可读摘要（台账/审批展示）
     * @param reason        申请理由（命中审批开关时必填）
     */
    record SubmitExportCommand(String objectCode, Map<String, String> filters,
                               String filterSummary, String reason) {
    }

    /**
     * 发起结果：{@code SUBMITTED} 已直接投任务（taskId 非空）；
     * {@code PENDING_APPROVAL} 已落审批台账（requestId 非空）。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class SubmitExportResult {
        private Status status;
        private Long taskId;
        private Long requestId;

        public enum Status { SUBMITTED, PENDING_APPROVAL }
    }

    /** 可导出对象选项（name/module 取自 {@code ExportObjectContributor} 声明）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ExportObjectOption {
        private String code;
        private String name;
        private String module;
        private boolean requiresApproval;

    }
}
