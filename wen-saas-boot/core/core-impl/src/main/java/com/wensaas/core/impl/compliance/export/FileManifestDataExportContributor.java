package com.wensaas.core.impl.compliance.export;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.compliance.spi.DataExportContributor;
import com.wensaas.core.api.compliance.spi.DataExportSink;
import com.wensaas.core.impl.file.SysFile;
import com.wensaas.core.impl.file.SysFileMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全量导出 · 文件中心清单贡献器。落 {@code files/files-manifest.json}：本租户
 * 活跃文件的元数据清单（文件名 / 类型 / 字节 / 归属 / 上传时间），不含文件二进制本身——文件实体
 * 仍由文件中心独立分发，全量包仅提供清单，避免包体爆炸。掩码模式不影响（元数据非敏感字段）。
 */
@Component
public class FileManifestDataExportContributor implements DataExportContributor {

    private final SysFileMapper fileMapper;

    public FileManifestDataExportContributor(SysFileMapper fileMapper) {
        this.fileMapper = fileMapper;
    }

    @Override
    public String section() {
        return "files";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public void contribute(DataExportSink sink) {
        List<SysFile> files = fileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getUploadState, "active")
                .orderByDesc(SysFile::getUploadedAt));

        List<Map<String, Object>> rows = new ArrayList<>(files.size());
        for (SysFile f : files) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fileId", f.getFileId());
            row.put("fileName", f.getFileName());
            row.put("contentType", f.getMimeType());
            row.put("size", f.getSizeBytes());
            row.put("visibility", f.getVisibility());
            row.put("ownerModule", f.getOwnerModule());
            row.put("ownerRefType", f.getOwnerRefType());
            row.put("ownerRefId", f.getOwnerRefId());
            row.put("folderPath", f.getFolderPath());
            row.put("uploadedAt", f.getUploadedAt());
            rows.add(row);
        }

        sink.putText("files-manifest.json", JSON.toJSONString(rows, JSONWriter.Feature.PrettyFormat));
        sink.count("文件", rows.size());
    }
}
