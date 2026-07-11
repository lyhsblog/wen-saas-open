package com.wensaas.core.api.compliance.spi;

/**
 * 全量数据导出贡献者 SPI（导出内容由业务方提供实现）。
 *
 * <p>各业务模块 / 底座子域实现本接口，把自己管辖的数据写进客户全量导出包。底座的
 * {@code DataExportTaskHandler}（任务中心异步执行）聚合所有 {@code DataExportContributor} Bean，
 * 按 {@link #order()} 排序依次调用 {@link #contribute(DataExportSink)}，把各 section 归集成一个
 * zip 落文件中心，再回写申请台账 + 站内信通知主管理员。
 *
 * <p>实现要求：
 * <ul>
 *   <li>{@code @Component}（或 {@code @Bean}）声明，运行时处于提交方租户上下文（{@code TenantContext} 已 set），
 *       直接用各自 Mapper 查询即随多租户改写自动收敛到本租户</li>
 *   <li>按 {@link DataExportSink#mode()} 决定敏感字段完整 / 脱敏输出</li>
 *   <li>大数据量应分页 / 限量，避免 OOM（v1 建议单 section 设上限）</li>
 *   <li>抛 RuntimeException 会使整个导出任务置 FAILED；非关键 section 应自行容错</li>
 * </ul>
 */
public interface DataExportContributor {

    /** 导出包内的 section 子目录名（小写短横线，如 {@code members}、{@code files}、{@code crm}）。 */
    String section();

    /** 执行顺序（升序）；底座内置 section 用 100 段，业务模块建议 ≥ 500。 */
    default int order() {
        return 100;
    }

    /** 写入本 section 的数据条目并登记概要计数。 */
    void contribute(DataExportSink sink);
}
