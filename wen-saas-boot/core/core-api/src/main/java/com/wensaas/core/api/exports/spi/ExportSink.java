package com.wensaas.core.api.exports.spi;

import java.util.List;

/**
 * 导出行接收器。由导出执行框架实现（流式写 xlsx），{@link ExportDataProvider#export} 逐行调用。
 */
public interface ExportSink {

    /** 写一行（cells 顺序与 {@link ExportDataProvider#columns()} 一致；null 视为空串）。 */
    void row(List<String> cells);

    /**
     * 是否应停止产出（用户取消任务 / 触达行数上限）。
     * 分页拉取类 provider 应在每页循环边界检查，true 时尽快返回。
     */
    boolean stopped();
}
