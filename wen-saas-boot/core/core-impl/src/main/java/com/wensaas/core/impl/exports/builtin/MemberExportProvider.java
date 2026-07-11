package com.wensaas.core.impl.exports.builtin;

import com.wensaas.core.api.auth.permission.MemberAdminService;
import com.wensaas.core.api.exports.spi.ExportColumn;
import com.wensaas.core.api.exports.spi.ExportDataProvider;
import com.wensaas.core.api.exports.spi.ExportSink;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 成员列表导出（导出对象 {@code member}，与 {@code BaseExportObjectContributor} 声明对齐，
 * 默认需审批）。复用 {@link MemberAdminService#listMembers}——手机 / 邮箱与列表页同口径
 * <b>掩码输出</b>（明文出口仅 {@code revealContact}，导出不放大敏感面）。
 *
 * <p>filters：{@code deptId} / {@code includeSubDepts} / {@code keyword} / {@code status} / {@code roleId}
 * （与成员列表页筛选一致）。
 */
public class MemberExportProvider implements ExportDataProvider {

    private static final int PAGE_SIZE = 500;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MemberAdminService memberService;

    public MemberExportProvider(MemberAdminService memberService) {
        this.memberService = memberService;
    }

    @Override
    public String objectCode() {
        return "member";
    }

    @Override
    public String requiredPermission() {
        return "org.member.list";
    }

    @Override
    public List<ExportColumn> columns() {
        return List.of(
                new ExportColumn("username", "账号"),
                new ExportColumn("realName", "姓名"),
                new ExportColumn("mobile", "手机号"),
                new ExportColumn("email", "邮箱"),
                new ExportColumn("mainDeptName", "主部门"),
                new ExportColumn("roleNames", "角色"),
                new ExportColumn("positionName", "职位"),
                new ExportColumn("status", "状态"),
                new ExportColumn("lastLoginAt", "最后登录时间"),
                new ExportColumn("createdAt", "创建时间"));
    }

    @Override
    public long estimateCount(Map<String, String> filters) {
        return memberService.listMembers(query(filters, 1, 1)).total();
    }

    @Override
    public void export(Map<String, String> filters, ExportSink sink) {
        int page = 1;
        while (!sink.stopped()) {
            MemberAdminService.MemberPage result =
                    memberService.listMembers(query(filters, page, PAGE_SIZE));
            for (MemberAdminService.MemberListItem m : result.items()) {
                sink.row(Arrays.asList(
                        m.username(),
                        m.realName(),
                        m.mobile(),
                        m.email(),
                        m.mainDeptName(),
                        m.roleNames() == null ? null : String.join("、", m.roleNames()),
                        m.positionName(),
                        m.status() == 1 ? "启用" : "停用",
                        m.lastLoginAt() == null ? null : TIME.format(m.lastLoginAt()),
                        m.createdAt() == null ? null : TIME.format(m.createdAt())));
            }
            if (result.items().size() < PAGE_SIZE) {
                return;
            }
            page++;
        }
    }

    private static MemberAdminService.MemberQuery query(Map<String, String> f, int page, int size) {
        return new MemberAdminService.MemberQuery(
                parseLong(f.get("deptId")),
                Boolean.parseBoolean(f.getOrDefault("includeSubDepts", "false")),
                blankToNull(f.get("keyword")),
                parseInt(f.get("status")),
                parseLong(f.get("roleId")),
                page, size);
    }

    private static Long parseLong(String s) {
        return s == null || s.isBlank() ? null : Long.valueOf(s);
    }

    private static Integer parseInt(String s) {
        return s == null || s.isBlank() ? null : Integer.valueOf(s);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
