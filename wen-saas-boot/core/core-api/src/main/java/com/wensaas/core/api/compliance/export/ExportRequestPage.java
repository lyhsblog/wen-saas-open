package com.wensaas.core.api.compliance.export;

import java.util.List;

/** 超量导出审批分页结果。 */
public record ExportRequestPage(List<ExportRequestView> items, long total, int page, int size) {

    public static ExportRequestPage empty(int page, int size) {
        return new ExportRequestPage(List.of(), 0, page, size);
    }
}
