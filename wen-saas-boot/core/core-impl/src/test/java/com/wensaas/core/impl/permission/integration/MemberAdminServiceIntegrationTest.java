package com.wensaas.core.impl.permission.integration;

import com.wensaas.core.api.auth.org.DeptAdminException;
import com.wensaas.core.api.auth.org.DeptAdminService;
import com.wensaas.core.api.auth.permission.MemberAdminException;
import com.wensaas.core.api.auth.permission.MemberAdminService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.permission.eval.PermissionCacheEvictor;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 成员管理 + 部门负责人 集成测试。
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberAdminServiceIntegrationTest {

    private static final long TENANT_ID = 9801L;

    @Autowired JdbcTemplate jdbc;
    @Autowired MemberAdminService service;
    @Autowired DeptAdminService deptService;
    @Autowired PermissionCacheEvictor evictor;

    @BeforeAll
    void setupClass() {
        cleanup();
    }

    @AfterAll
    void teardownClass() {
        cleanup();
        TenantContext.clear();
    }

    @BeforeEach
    void setTenant() {
        cleanup();
        jdbc.update("INSERT INTO sys_tenant (id, code, name, status) VALUES (?, ?, ?, 1)",
                TENANT_ID, "t9801", "测试租户9801");
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        evictor.evictAll();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_dept_leader WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_user_dept WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_user_role WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_user WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_dept WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_password_reset_token WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_tenant WHERE id = ?", TENANT_ID);
    }

    private long dept(String name, long parent) {
        return deptService.create(new DeptAdminService.CreateDeptRequest(parent, name, 0)).id();
    }

    @Test
    void createMember_issuesActivationToken_andBindsDeptRole() {
        long rd = dept("研发中心", 0);
        long be = dept("后端组", rd);

        MemberAdminService.CreatedMember created = service.createMember(new MemberAdminService.CreateMemberCommand(
                "zhangsan", "张三", "13800000001", "zhangsan@example.com", rd, List.of(be), List.of(), null, null));
        assertThat(created.activationToken()).isNotBlank();

        MemberAdminService.MemberDetail detail = service.getMember(created.id());
        assertThat(detail.mainDept().deptId()).isEqualTo(rd);
        assertThat(detail.secondaryDepts()).extracting(MemberAdminService.DeptBinding::deptId).containsExactly(be);
        // TransFiller 深递归回填嵌套 DeptBinding.deptName
        assertThat(detail.mainDept().deptName()).isEqualTo("研发中心");
        assertThat(detail.secondaryDepts())
                .extracting(MemberAdminService.DeptBinding::deptName).containsExactly("后端组");
        // 详情默认脱敏；明文走 revealContact。
        assertThat(detail.mobile()).isEqualTo("138****0001");
        assertThat(detail.email()).isEqualTo("z***n@example.com");
        // username 自 ba5a18e 起不再作为敏感字段、列表/详情返明文；未触发登录失败 → 未锁定。
        assertThat(detail.username()).isEqualTo("zhangsan");
        assertThat(detail.loginLocked()).isFalse();
    }

    @Test
    void positionCode_roundTrips_nameFallsBackToCodeWhenDictEmpty() {
        long rd = dept("研发", 0);
        // 岗位字典默认空（POSITION 不预置平台项）→ positionName 回退 code 文本快照
        long id = service.createMember(new MemberAdminService.CreateMemberCommand(
                "withpos", "有岗位", null, null, rd, List.of(), List.of(), "高级工程师", null)).id();

        MemberAdminService.MemberDetail detail = service.getMember(id);
        assertThat(detail.positionCode()).isEqualTo("高级工程师");
        assertThat(detail.positionName()).isEqualTo("高级工程师");

        // 编辑置空岗位
        service.updateMember(id, new MemberAdminService.UpdateMemberCommand(
                null, null, null, null, null, null, "", null));
        assertThat(service.getMember(id).positionCode()).isNull();
    }

    @Test
    void revealContact_returnsPlaintext_whileDetailStaysMasked() {
        long rd = dept("研发中心", 0);
        long id = service.createMember(new MemberAdminService.CreateMemberCommand(
                "lisi", "李四", "13900000002", "lisi@example.com", rd, List.of(), List.of(), null, null)).id();

        assertThat(service.getMember(id).mobile()).isEqualTo("139****0002");

        MemberAdminService.ContactReveal reveal = service.revealContact(id);
        assertThat(reveal.mobile()).isEqualTo("13900000002");
        assertThat(reveal.email()).isEqualTo("lisi@example.com");
    }

    @Test
    void updateMember_maskedPlaceholder_treatedAsUnchanged_doesNotOverwrite() {
        long rd = dept("研发中心", 0);
        long id = service.createMember(new MemberAdminService.CreateMemberCommand(
                "wangwu", "王五", "13700000003", "wangwu@example.com", rd, List.of(), List.of(), null, null)).id();

        // 编辑只改姓名，手机/邮箱回填的是掩码占位 → 不得覆盖真实明文。
        service.updateMember(id, new MemberAdminService.UpdateMemberCommand(
                "王五改", "137****0003", "w***u@example.com", null, null, null, null, null));
        assertThat(service.revealContact(id).mobile()).isEqualTo("13700000003");
        assertThat(service.revealContact(id).email()).isEqualTo("wangwu@example.com");
        assertThat(service.getMember(id).realName()).isEqualTo("王五改");

        // 真正填了新号码 → 正常更新。
        service.updateMember(id, new MemberAdminService.UpdateMemberCommand(
                null, "13700009999", null, null, null, null, null, null));
        assertThat(service.revealContact(id).mobile()).isEqualTo("13700009999");
        assertThat(service.revealContact(id).email()).isEqualTo("wangwu@example.com");
    }

    @Test
    void listMembers_filtersByDept_includingSubDepts() {
        long rd = dept("研发", 0);
        long be = dept("后端组", rd);
        long zhang = service.createMember(new MemberAdminService.CreateMemberCommand(
                "zhang", "张", null, null, be, List.of(), List.of(), null, null)).id();

        // 直接按子部门过滤
        assertThat(service.listMembers(new MemberAdminService.MemberQuery(be, false, null, null, null, 1, 20)).items())
                .extracting(MemberAdminService.MemberListItem::id).containsExactly(zhang);
        // listMembers 经 transFiller.fill 回填 mainDeptName（收口原 deptNameMap）
        assertThat(service.listMembers(new MemberAdminService.MemberQuery(be, false, null, null, null, 1, 20)).items())
                .extracting(MemberAdminService.MemberListItem::mainDeptName).containsExactly("后端组");
        // 按父部门 + 含子部门
        assertThat(service.listMembers(new MemberAdminService.MemberQuery(rd, true, null, null, null, 1, 20)).items())
                .extracting(MemberAdminService.MemberListItem::id).containsExactly(zhang);
        // 按父部门不含子部门 → 查不到
        assertThat(service.listMembers(new MemberAdminService.MemberQuery(rd, false, null, null, null, 1, 20)).items())
                .isEmpty();
    }

    @Test
    void duplicateUsername_throws() {
        long rd = dept("研发", 0);
        service.createMember(new MemberAdminService.CreateMemberCommand("dup", "甲", null, null, rd, null, null, null, null));
        assertThatThrownBy(() -> service.createMember(new MemberAdminService.CreateMemberCommand(
                "dup", "乙", null, null, rd, null, null, null, null)))
                .isInstanceOf(MemberAdminException.class)
                .extracting(e -> ((MemberAdminException) e).getCode())
                .isEqualTo(MemberAdminException.Code.USERNAME_CONFLICT);
    }

    @Test
    void deptLeader_setAndAutoCleanupOnRemoval() {
        long rd = dept("研发", 0);
        long be = dept("后端组", rd);
        long zhang = service.createMember(new MemberAdminService.CreateMemberCommand(
                "zhang2", "张二", null, null, be, List.of(), List.of(), null, null)).id();

        // 设为后端组负责人
        deptService.setLeaders(be, List.of(zhang));
        assertThat(deptService.listLeaders(be)).extracting(DeptAdminService.DeptLeaderRef::userId).containsExactly(zhang);

        // 把张二主部门改到研发、移出后端组 → 负责人身份自动撤销
        service.manageDepts(zhang, new MemberAdminService.ManageDeptsCommand(rd, List.of()));
        assertThat(deptService.listLeaders(be)).isEmpty();
    }

    @Test
    void setLeader_nonMember_throws() {
        long rd = dept("研发", 0);
        long be = dept("后端组", rd);
        long other = service.createMember(new MemberAdminService.CreateMemberCommand(
                "other", "外人", null, null, rd, List.of(), List.of(), null, null)).id();
        // other 不在后端组
        assertThatThrownBy(() -> deptService.setLeaders(be, List.of(other)))
                .isInstanceOf(DeptAdminException.class)
                .extracting(e -> ((DeptAdminException) e).code())
                .isEqualTo(DeptAdminException.Code.LEADER_NOT_MEMBER);
    }

    @Test
    void disable_retainsDeptLeader_andExposesForReminder() {
        long rd = dept("研发", 0);
        long be = dept("后端组", rd);
        long li = service.createMember(new MemberAdminService.CreateMemberCommand(
                "li4", "李四", null, null, be, List.of(), List.of(), null, null)).id();
        deptService.setLeaders(be, List.of(li));

        // 停用李四：负责人身份保留，不自动卸任
        service.setStatus(li, false, 99999L);
        assertThat(deptService.listLeaders(be))
                .extracting(DeptAdminService.DeptLeaderRef::userId).containsExactly(li);

        // detail / list 暴露负责人身份供前端高亮提醒
        MemberAdminService.MemberDetail detail = service.getMember(li);
        assertThat(detail.status()).isZero();
        assertThat(detail.leaderDepts()).extracting(MemberAdminService.DeptBinding::deptId).containsExactly(be);
        assertThat(service.listMembers(new MemberAdminService.MemberQuery(be, false, null, null, null, 1, 20)).items())
                .filteredOn(m -> m.id() == li)
                .singleElement()
                .extracting(MemberAdminService.MemberListItem::leaderDeptCount).isEqualTo(1);

        // 恢复启用：身份仍在，立即复权、无需重配
        service.setStatus(li, true, 99999L);
        assertThat(deptService.listLeaders(be))
                .extracting(DeptAdminService.DeptLeaderRef::userId).containsExactly(li);
    }

    @Test
    void setStatus_cannotDisableSelfOrOwner() {
        long rd = dept("研发", 0);
        long me = service.createMember(new MemberAdminService.CreateMemberCommand(
                "me", "我", null, null, rd, List.of(), List.of(), null, null)).id();
        // 不能停用自己
        assertThatThrownBy(() -> service.setStatus(me, false, me))
                .isInstanceOf(MemberAdminException.class)
                .extracting(e -> ((MemberAdminException) e).getCode())
                .isEqualTo(MemberAdminException.Code.CANNOT_DISABLE_SELF);

        // 设为主管理员后不可停用
        jdbc.update("UPDATE sys_tenant SET main_admin_user_id = ? WHERE id = ?", me, TENANT_ID);
        assertThatThrownBy(() -> service.setStatus(me, false, 99999L))
                .isInstanceOf(MemberAdminException.class)
                .extracting(e -> ((MemberAdminException) e).getCode())
                .isEqualTo(MemberAdminException.Code.OWNER_PROTECTED);
    }
}
