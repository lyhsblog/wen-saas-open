package com.wensaas.core.impl.compliance.export;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.wensaas.core.api.compliance.export.DataExportMode;
import com.wensaas.core.api.compliance.export.DataExportStatus;
import com.wensaas.core.api.compliance.spi.DataExportContributor;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.task.TaskContext;
import com.wensaas.core.api.task.TaskHandler;
import com.wensaas.core.impl.compliance.ComplianceSupport;
import com.wensaas.core.impl.compliance.export.DataExportRequest;
import com.wensaas.core.impl.compliance.export.DataExportRequestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 客户全量数据导出（GDPR-style）任务中心处理器：聚合所有
 * {@link DataExportContributor} 归集成一个 zip，经文件中心 {@link FileService} 落库，回写
 * {@code sys_data_export_request} 终态（文件 / 令牌 / 7 天有效期 / 内容概要）并站内信通知主管理员。
 *
 * <p>异步：由 {@code DataExportServiceImpl.submit} 投递任务（handlerCode {@link #HANDLER_CODE}），
 * 经 RocketMQ → TaskRunner 在提交方租户上下文执行；集成测试直接调 {@code TaskRunner.run}。
 * 抛异常使任务置 FAILED，同时把申请台账标 FAILED + 失败原因。
 */
public class DataExportTaskHandler implements TaskHandler {

    public static final String HANDLER_CODE = "compliance.task.data_export";

    private static final Logger log = LoggerFactory.getLogger(DataExportTaskHandler.class);
    private static final int DOWNLOAD_VALID_DAYS = 7;
    private static final int MAX_ERROR_LEN = 480;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final DataExportRequestMapper requestMapper;
    private final List<DataExportContributor> contributors;
    private final FileService fileService;
    private final ComplianceSupport support;

    public DataExportTaskHandler(DataExportRequestMapper requestMapper,
                                 List<DataExportContributor> contributors,
                                 FileService fileService,
                                 ComplianceSupport support) {
        this.requestMapper = requestMapper;
        this.contributors = contributors;
        this.fileService = fileService;
        this.support = support;
    }

    @Override
    public String handlerCode() {
        return HANDLER_CODE;
    }

    /** 本 handler 完成时自发带下载链接的 {@code base.compliance.data_export.completed} 通知，抑制通用终态通知免重复。 */
    @Override
    public boolean suppressTerminalNotification() {
        return true;
    }

    @Override
    public void execute(TaskContext ctx) {
        long requestId = Long.parseLong(String.valueOf(ctx.params().get("requestId")));
        DataExportRequest r = requestMapper.selectById(requestId);
        if (r == null) {
            log.warn("[compliance] 全量导出任务找不到申请 requestId={}", requestId);
            return;
        }
        try {
            DataExportMode mode = DataExportMode.of(r.getDataMode());
            DataExportCollector collector = new DataExportCollector(ctx.tenantId(), mode);

            collector.section("");
            collector.putText("metadata.json", buildMetadata(r, mode));
            ctx.updateProgress(10, 0);

            List<DataExportContributor> ordered = contributors.stream()
                    .sorted(Comparator.comparingInt(DataExportContributor::order))
                    .toList();
            int done = 0;
            for (DataExportContributor c : ordered) {
                collector.section(c.section());
                c.contribute(collector);
                done++;
                ctx.updateProgress(10 + (int) (70.0 * done / Math.max(1, ordered.size())), done);
            }

            String itemSummary = JSON.toJSONString(collector.counts());
            collector.section("");
            collector.putText("summary.json", itemSummary);

            byte[] zip = zip(collector.entries());
            ctx.updateProgress(85, ordered.size());

            String fileName = String.format("tenant-%d-full-export-%s.zip",
                    r.getTenantId(), LocalDate.now(SHANGHAI).toString().replace("-", ""));
            FileUploadResult uploaded = fileService.uploadDirect(new FileUploadRequest(
                    fileName, "application/zip", zip.length,
                    new ByteArrayInputStream(zip), FileVisibility.BUSINESS_ONLY,
                    "compliance", "data_export", r.getId(), null));

            Instant now = Instant.now();
            r.setStatus(DataExportStatus.COMPLETED.name());
            r.setFileId(uploaded.fileId());
            r.setFileName(fileName);
            r.setFileSize((long) zip.length);
            r.setItemSummary(itemSummary);
            r.setDownloadToken(UUID.randomUUID().toString().replace("-", ""));
            r.setExpiresAt(now.plus(DOWNLOAD_VALID_DAYS, ChronoUnit.DAYS));
            r.setCompletedAt(now);
            r.setErrorMessage(null);
            r.setUpdatedAt(LocalDateTime.now());
            r.setUpdatedBy(r.getApplicantId());
            requestMapper.updateById(r);

            ctx.updateProgress(100, ordered.size());
            ctx.recordSuccess(1);
            ctx.setResult(Map.of(
                    "fileId", uploaded.fileId(),
                    "fileSize", zip.length,
                    "items", collector.counts()));

            support.notify(r.getTenantId(), List.of(r.getApplicantId()),
                    "base.compliance.data_export.completed",
                    Map.of("request_no", r.getRequestNo(), "expires_days", String.valueOf(DOWNLOAD_VALID_DAYS)),
                    r.getId(), "/system/compliance?tab=dataExport&selected=" + r.getId());
            log.info("[compliance] 全量导出完成 requestId={} fileId={} bytes={}",
                    requestId, uploaded.fileId(), zip.length);
        } catch (RuntimeException e) {
            log.error("[compliance] 全量导出失败 requestId={} reason={}", requestId, e.getMessage(), e);
            r.setStatus(DataExportStatus.FAILED.name());
            r.setErrorMessage(truncate(e.getMessage()));
            r.setUpdatedAt(LocalDateTime.now());
            requestMapper.updateById(r);
            throw e;
        }
    }

    /** 包根 metadata.json：申请信息 + 生成时间 + 敏感字段处理模式 + 范围说明。 */
    private static String buildMetadata(DataExportRequest r, DataExportMode mode) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestNo", r.getRequestNo());
        meta.put("tenantId", String.valueOf(r.getTenantId()));
        meta.put("reason", r.getReason());
        meta.put("dataMode", mode.name());
        meta.put("dataModeLabel", mode.label());
        meta.put("scopeSummary", r.getScopeSummary());
        meta.put("applicant", r.getApplicantName());
        meta.put("requestedAt", String.valueOf(r.getRequestedAt()));
        meta.put("generatedAt", Instant.now().toString());
        meta.put("downloadValidDays", DOWNLOAD_VALID_DAYS);
        meta.put("note", "本包敏感字段按申请人选择处理；彻底销户需走运营线下流程。");
        return JSON.toJSONString(meta, JSONWriter.Feature.PrettyFormat);
    }

    private static byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("打包 zip 失败", ex);
        }
        return bos.toByteArray();
    }

    private static String truncate(String s) {
        if (s == null) {
            return "打包失败";
        }
        return s.length() > MAX_ERROR_LEN ? s.substring(0, MAX_ERROR_LEN) : s;
    }
}
