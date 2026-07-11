package com.wensaas.core.web.compliance;

import java.util.List;

/** 超量导出审批分页 Web 视图。 */
public record ExportRequestPageWebView(List<ExportRequestWebView> items, long total, int page, int size) {
}
