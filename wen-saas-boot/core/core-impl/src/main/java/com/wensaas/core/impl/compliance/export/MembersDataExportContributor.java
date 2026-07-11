package com.wensaas.core.impl.compliance.export;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.wensaas.core.api.audit.Sensitives;
import com.wensaas.core.api.auth.user.MemberExportQuery;
import com.wensaas.core.api.auth.user.MemberExportQuery.MemberExportRecord;
import com.wensaas.core.api.compliance.export.DataExportMode;
import com.wensaas.core.api.compliance.spi.DataExportContributor;
import com.wensaas.core.api.compliance.spi.DataExportSink;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全量导出 · 组织成员贡献器。落 {@code members/members.json}：本租户全部成员。
 *
 * <p>成员档由 {@link MemberExportQuery#exportableMembers()} 收口（auth domain 读服务，含明文
 * mobile/email）；当 {@link DataExportMode#MASKED} 时再按 {@link Sensitives} 掩码（{@code 138****5678} /
 * {@code a***i@example.com}），{@link DataExportMode#FULL} 时输出明文。
 */
@Component
public class MembersDataExportContributor implements DataExportContributor {

    private final MemberExportQuery memberExport;

    public MembersDataExportContributor(MemberExportQuery memberExport) {
        this.memberExport = memberExport;
    }

    @Override
    public String section() {
        return "members";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void contribute(DataExportSink sink) {
        boolean masked = sink.mode() == DataExportMode.MASKED;
        List<MemberExportRecord> users = memberExport.exportableMembers();

        List<Map<String, Object>> rows = new ArrayList<>(users.size());
        for (MemberExportRecord u : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.id() == null ? null : String.valueOf(u.id()));
            row.put("username", u.username());
            row.put("realName", u.realName());
            row.put("mobile", masked ? Sensitives.maskPhone(u.mobile()) : u.mobile());
            row.put("email", masked ? Sensitives.maskEmail(u.email()) : u.email());
            row.put("empNo", u.empNo());
            row.put("status", u.status());
            row.put("createdAt", u.createdAt());
            rows.add(row);
        }

        sink.putText("members.json", JSON.toJSONString(rows, JSONWriter.Feature.PrettyFormat));
        sink.count("成员", rows.size());
    }
}
