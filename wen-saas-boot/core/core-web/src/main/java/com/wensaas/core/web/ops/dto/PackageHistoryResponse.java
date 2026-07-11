package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 套餐变更历史响应体（OPS-P-012）。
 *
 * <p>数据源 {@code sys_operation_log}，按 {@code module='package' + object_type='sys_package' + object_id=code}
 * 过滤、时间倒序。需查改名/加模块等变更明细时，前端用 {@code id + createdAt} 调运营操作日志详情接口
 * （{@code GET /api/v1/ops/operation-logs/{id}?createdAt=}）取 changeBefore/changeAfter。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class PackageHistoryResponse {
    private List<PackageHistoryItemView> items;
    private long total;
    private int page;
    private int pageSize;

    /** {@code action}=create/update/publish/unpublish/archive/delete/copy。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class PackageHistoryItemView {
        private String id;
        private String action;
        private String operatorId;
        private String operatorName;
        private String objectDesc;
        @Schema(description = "HTTP 状态码：200 成功 / 500 失败")
        private Integer statusCode;
        private String createdAt;
    }
}
