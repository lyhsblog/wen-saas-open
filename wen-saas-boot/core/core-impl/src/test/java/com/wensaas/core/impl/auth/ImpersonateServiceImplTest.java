package com.wensaas.core.impl.auth;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;

import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.audit.AuditObjectRef;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.audit.LoginAuditEvent;
import com.wensaas.core.api.auth.login.ImpersonateException;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.auth.org.DeptQueryService;
import com.wensaas.core.impl.auth.impersonate.ImpersonateNonceData;
import com.wensaas.core.impl.auth.impersonate.ImpersonateNonceStore;
import com.wensaas.core.impl.auth.impersonate.ImpersonateService;
import com.wensaas.core.impl.auth.impersonate.ImpersonateServiceImpl;
import com.wensaas.core.impl.auth.login.AuthService;
import com.wensaas.core.impl.auth.session.JwtTokenProvider;
import com.wensaas.core.impl.auth.session.Session;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImpersonateServiceImplTest {

    private SessionStore sessionStore;
    private ImpersonateNonceStore nonceStore;
    private JwtTokenProvider jwtTokenProvider;
    private SysUserMapper userMapper;
    private TenantQueryService tenantQuery;
    private DeptQueryService deptQuery;
    private com.wensaas.core.api.ops.access.OpsAccessContextResolver accessResolver;
    private CapturingRecorder recorder;
    private ImpersonateServiceImpl service;

    @BeforeEach
    void setUp() {
        sessionStore = mock(SessionStore.class);
        nonceStore = mock(ImpersonateNonceStore.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        userMapper = mock(SysUserMapper.class);
        tenantQuery = mock(TenantQueryService.class);
        deptQuery = mock(DeptQueryService.class);
        accessResolver = mock(com.wensaas.core.api.ops.access.OpsAccessContextResolver.class);
        // 默认：超管（OPS_ALL），数据范围守卫直接放行
        when(accessResolver.require()).thenReturn(new com.wensaas.core.api.ops.access.OpsAccessContext(
                1001L, com.wensaas.core.api.ops.access.OpsDataScope.OPS_ALL, java.util.Set.of()));
        recorder = new CapturingRecorder();
        service = new ImpersonateServiceImpl(sessionStore, nonceStore, jwtTokenProvider,
                userMapper, tenantQuery, deptQuery, accessResolver, recorder,
                mock(com.wensaas.core.api.notification.send.MessageService.class));

        // 默认：运营 UserContext + 非代登录 ops session
        UserContext.set(new UserPrincipal(1001L, 0L, "opsAdmin", "ops-sid", false, null, false, null));
        Session opsSession = new Session("ops-sid", 1001L, 0L, "opsAdmin", false, null,
                Instant.now(), Instant.now(), null, null);
        when(sessionStore.findBySessionId("ops-sid")).thenReturn(Optional.of(opsSession));
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
        AuditObjects.clear();
    }

    // ===== startImpersonation 校验分支 =====

    @Test
    void startImpersonation_nestedNotAllowed() {
        Session nestedOpsSession = new Session("ops-sid", 1001L, 0L, "opsAdmin", false, null,
                Instant.now(), Instant.now(), "some-original-sid", null);
        when(sessionStore.findBySessionId("ops-sid")).thenReturn(Optional.of(nestedOpsSession));

        assertThatThrownBy(() -> service.startImpersonation(100L, 200L))
                .isInstanceOf(ImpersonateException.class)
                .satisfies(e -> assertThat(((ImpersonateException) e).code())
                        .isEqualTo(ImpersonateException.Code.IMPERSONATE_NESTED_NOT_ALLOWED));
        verify(nonceStore, never()).issue(any());
    }

    @Test
    void startImpersonation_tenantNotFound() {
        when(tenantQuery.tenantById(100L)).thenReturn(null);

        assertThatThrownBy(() -> service.startImpersonation(100L, 200L))
                .isInstanceOf(ImpersonateException.class)
                .satisfies(e -> assertThat(((ImpersonateException) e).code())
                        .isEqualTo(ImpersonateException.Code.IMPERSONATE_TARGET_TENANT_NOT_FOUND));
    }

    @Test
    void startImpersonation_tenantInactive() {
        when(tenantQuery.tenantById(100L)).thenReturn(tenant(100L, "acme", 0));

        assertThatThrownBy(() -> service.startImpersonation(100L, 200L))
                .isInstanceOf(ImpersonateException.class)
                .satisfies(e -> assertThat(((ImpersonateException) e).code())
                        .isEqualTo(ImpersonateException.Code.IMPERSONATE_TARGET_TENANT_INACTIVE));
    }

    @Test
    void startImpersonation_userNotFound() {
        when(tenantQuery.tenantById(100L)).thenReturn(tenant(100L, "acme", 1));
        when(userMapper.selectById(200L)).thenReturn(null);

        assertThatThrownBy(() -> service.startImpersonation(100L, 200L))
                .isInstanceOf(ImpersonateException.class)
                .satisfies(e -> assertThat(((ImpersonateException) e).code())
                        .isEqualTo(ImpersonateException.Code.IMPERSONATE_TARGET_USER_NOT_FOUND));
    }

    @Test
    void startImpersonation_userNotInTenant() {
        when(tenantQuery.tenantById(100L)).thenReturn(tenant(100L, "acme", 1));
        when(userMapper.selectById(200L)).thenReturn(user(200L, 999L, "member", 1));

        assertThatThrownBy(() -> service.startImpersonation(100L, 200L))
                .isInstanceOf(ImpersonateException.class)
                .satisfies(e -> assertThat(((ImpersonateException) e).code())
                        .isEqualTo(ImpersonateException.Code.IMPERSONATE_USER_NOT_IN_TENANT));
    }

    @Test
    void startImpersonation_targetIsOps() {
        // 路径 tenantId=0，user 也在 tenantId=0 → IS_OPS
        when(tenantQuery.tenantById(0L)).thenReturn(tenant(0L, "__ops__", 1));
        when(userMapper.selectById(200L)).thenReturn(user(200L, 0L, "opsUser", 1));

        assertThatThrownBy(() -> service.startImpersonation(0L, 200L))
                .isInstanceOf(ImpersonateException.class)
                .satisfies(e -> assertThat(((ImpersonateException) e).code())
                        .isEqualTo(ImpersonateException.Code.IMPERSONATE_TARGET_IS_OPS));
    }

    @Test
    void startImpersonation_userDisabled() {
        when(tenantQuery.tenantById(100L)).thenReturn(tenant(100L, "acme", 1));
        when(userMapper.selectById(200L)).thenReturn(user(200L, 100L, "member", 0));

        assertThatThrownBy(() -> service.startImpersonation(100L, 200L))
                .isInstanceOf(ImpersonateException.class)
                .satisfies(e -> assertThat(((ImpersonateException) e).code())
                        .isEqualTo(ImpersonateException.Code.IMPERSONATE_TARGET_USER_DISABLED));
    }

    @Test
    void startImpersonation_ownedScope_notOwnedTenant_throwsNotInScope() {
        // OPS_OWNED（客户经理）代登录非名下租户（csm_user_id=2002 ≠ 自己 1001）→ 403 语义
        when(accessResolver.require()).thenReturn(new com.wensaas.core.api.ops.access.OpsAccessContext(
                1001L, com.wensaas.core.api.ops.access.OpsDataScope.OPS_OWNED, java.util.Set.of()));
        when(tenantQuery.tenantById(100L)).thenReturn(
                new TenantInfo(100L, "acme", "租户-acme", 1, null, null, null, null, 2002L));

        assertThatThrownBy(() -> service.startImpersonation(100L, 200L))
                .isInstanceOf(ImpersonateException.class)
                .satisfies(e -> assertThat(((ImpersonateException) e).code())
                        .isEqualTo(ImpersonateException.Code.IMPERSONATE_TENANT_NOT_IN_SCOPE));
        verify(nonceStore, never()).issue(any());
    }

    @Test
    void startImpersonation_ownedScope_ownedTenant_succeeds() {
        // OPS_OWNED 代登录名下租户（csm_user_id=自己）正常放行
        when(accessResolver.require()).thenReturn(new com.wensaas.core.api.ops.access.OpsAccessContext(
                1001L, com.wensaas.core.api.ops.access.OpsDataScope.OPS_OWNED, java.util.Set.of()));
        when(tenantQuery.tenantById(100L)).thenReturn(
                new TenantInfo(100L, "acme", "租户-acme", 1, null, null, null, null, 1001L));
        when(userMapper.selectById(200L)).thenReturn(user(200L, 100L, "member", 1));
        when(nonceStore.issue(any())).thenReturn("nonce-owned");

        assertThat(service.startImpersonation(100L, 200L).redeemNonce()).isEqualTo("nonce-owned");
    }

    @Test
    void startImpersonation_happyPath_returnsNonceIssued() {
        when(tenantQuery.tenantById(100L)).thenReturn(tenant(100L, "acme", 1));
        when(userMapper.selectById(200L)).thenReturn(user(200L, 100L, "member", 1));
        when(nonceStore.issue(any())).thenReturn("test-nonce-uuid");

        ImpersonateService.NonceIssued result = service.startImpersonation(100L, 200L);

        assertThat(result.redeemNonce()).isEqualTo("test-nonce-uuid");
        assertThat(result.targetTenantCode()).isEqualTo("acme");
        assertThat(result.expiresInSec()).isEqualTo(300L);
        verify(nonceStore).issue(argThat(d ->
                d.targetTenantId() == 100L && d.asUserId() == 200L
                        && d.operatorUserId() == 1001L && "ops-sid".equals(d.operatorSessionId())));
        // 登记审计主对象友好名：租户名 + 成员名（realName 空回退账号）→ object_desc 由其派生
        java.util.List<AuditObjectRef> refs = AuditObjects.drain();
        assertThat(refs).extracting(AuditObjectRef::name).containsExactly("租户-acme", "member");
    }

    // ===== redeemNonce =====

    @Test
    void redeemNonce_invalidNonce_throws() {
        when(nonceStore.redeem("bad-nonce")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeemNonce("bad-nonce"))
                .isInstanceOf(ImpersonateException.class)
                .satisfies(e -> assertThat(((ImpersonateException) e).code())
                        .isEqualTo(ImpersonateException.Code.IMPERSONATE_NONCE_INVALID));
    }

    @Test
    void redeemNonce_operatorSessionGone_throws() {
        ImpersonateNonceData nd = new ImpersonateNonceData(100L, 200L, 1001L, "ops-sid-gone", Instant.now());
        when(nonceStore.redeem("valid-nonce")).thenReturn(Optional.of(nd));
        when(sessionStore.findBySessionId("ops-sid-gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeemNonce("valid-nonce"))
                .isInstanceOf(ImpersonateException.class)
                .satisfies(e -> assertThat(((ImpersonateException) e).code())
                        .isEqualTo(ImpersonateException.Code.IMPERSONATE_OPERATOR_SESSION_GONE));
    }

    @Test
    void redeemNonce_happyPath_returnsLoginResult() {
        Session opsSession = new Session("ops-sid-r", 1001L, 0L, "opsAdmin", false, null,
                Instant.now(), Instant.now(), null, null);
        ImpersonateNonceData nd = new ImpersonateNonceData(100L, 200L, 1001L, "ops-sid-r", Instant.now());
        when(nonceStore.redeem("valid-nonce")).thenReturn(Optional.of(nd));
        when(sessionStore.findBySessionId("ops-sid-r")).thenReturn(Optional.of(opsSession));

        SysUser targetUser = user(200L, 100L, "tenantMember", 1);
        when(userMapper.selectById(200L)).thenReturn(targetUser);
        when(tenantQuery.tenantById(100L)).thenReturn(tenant(100L, "acme", 1));
        when(deptQuery.mainDeptIdOfUser(anyLong())).thenReturn(null);
        when(sessionStore.createImpersonatedSession(any(), eq("ops-sid-r"), eq("opsAdmin")))
                .thenReturn("imp-session-id");
        when(jwtTokenProvider.issue("imp-session-id")).thenReturn("imp-token");
        when(jwtTokenProvider.tokenTtl()).thenReturn(Duration.ofHours(2));

        AuthService.LoginResult result = service.redeemNonce("valid-nonce");

        assertThat(result.token()).isEqualTo("imp-token");
        assertThat(result.principal().tenantId()).isEqualTo(100L);
        assertThat(result.principal().username()).isEqualTo("tenantMember");
        assertThat(result.principal().sessionId()).isEqualTo("imp-session-id");
        // 运营操作人名快照进代登录 session（取自运营原 session 的 username）
        verify(sessionStore).createImpersonatedSession(any(), eq("ops-sid-r"), eq("opsAdmin"));
    }

    // ===== exitImpersonation =====

    @Test
    void exitImpersonation_notImpersonating_throws() {
        Session normalSession = new Session("cur-sid", 200L, 100L, "member", false, null,
                Instant.now(), Instant.now(), null, null);
        when(sessionStore.findBySessionId("cur-sid")).thenReturn(Optional.of(normalSession));

        assertThatThrownBy(() -> service.exitImpersonation("cur-sid"))
                .isInstanceOf(ImpersonateException.class)
                .satisfies(e -> assertThat(((ImpersonateException) e).code())
                        .isEqualTo(ImpersonateException.Code.EXIT_IMPERSONATION_NOT_IMPERSONATING));
    }

    @Test
    void exitImpersonation_originalSessionGone_throws() {
        Session impSession = new Session("cur-sid", 200L, 100L, "member", false, null,
                Instant.now(), Instant.now(), "orig-sid", null);
        when(sessionStore.findBySessionId("cur-sid")).thenReturn(Optional.of(impSession));
        when(sessionStore.findBySessionId("orig-sid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exitImpersonation("cur-sid"))
                .isInstanceOf(ImpersonateException.class)
                .satisfies(e -> assertThat(((ImpersonateException) e).code())
                        .isEqualTo(ImpersonateException.Code.EXIT_IMPERSONATION_ORIGINAL_GONE));
    }

    @Test
    void exitImpersonation_happyPath_invalidatesSessionAndAudits() {
        Session impSession = new Session("cur-sid", 200L, 100L, "member", false, null,
                Instant.now(), Instant.now(), "orig-sid", null);
        // 运营原会话携带 realName 快照 → 退出审计操作人列取 realName（与 @Auditable 口径一致）
        Session opsSession = new Session("orig-sid", 1001L, 0L, "opsAdmin", false, null,
                Instant.now(), Instant.now(), null, null, "平台超级管理员");
        when(sessionStore.findBySessionId("cur-sid")).thenReturn(Optional.of(impSession));
        when(sessionStore.findBySessionId("orig-sid")).thenReturn(Optional.of(opsSession));
        when(tenantQuery.tenantById(100L)).thenReturn(tenant(100L, "acme", 1));

        service.exitImpersonation("cur-sid");

        verify(sessionStore).invalidate("cur-sid");
        // 不再重签运营 token：tenant-web 与 ops-web 不同域，运营原 session 全程未动
        verify(jwtTokenProvider, never()).issue(anyString());
        assertThat(recorder.captured).isNotNull();
        assertThat(recorder.captured.tenantId()).isEqualTo(0L);
        assertThat(recorder.captured.targetTenantId()).isEqualTo(100L);
        assertThat(recorder.captured.action()).isEqualTo("退出代登录");
        assertThat(recorder.captured.operatorId()).isEqualTo(1001L);
        assertThat(recorder.captured.operatorName()).isEqualTo("平台超级管理员");
        // 对象友好名=租户名（修好对象列裸 ID）
        assertThat(recorder.captured.objectDesc()).isEqualTo("租户-acme");
    }

    @Test
    void exitImpersonation_opsSessionWithoutRealName_fallsBackToUsername() {
        Session impSession = new Session("cur-sid", 200L, 100L, "member", false, null,
                Instant.now(), Instant.now(), "orig-sid", null);
        Session opsSession = new Session("orig-sid", 1001L, 0L, "opsAdmin", false, null,
                Instant.now(), Instant.now(), null, null, null);
        when(sessionStore.findBySessionId("cur-sid")).thenReturn(Optional.of(impSession));
        when(sessionStore.findBySessionId("orig-sid")).thenReturn(Optional.of(opsSession));

        service.exitImpersonation("cur-sid");

        assertThat(recorder.captured.operatorName()).isEqualTo("opsAdmin");
    }

    // ===== helpers =====

    private TenantInfo tenant(Long id, String code, int status) {
        return new TenantInfo(id, code, "租户-" + code, status, null, null, null, null, null);
    }

    private SysUser user(Long id, Long tenantId, String username, int status) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setTenantId(tenantId);
        u.setUsername(username);
        u.setStatus(status);
        return u;
    }

    static class CapturingRecorder implements AuditLogRecorder {
        AuditEvent captured;

        @Override
        public void record(AuditEvent event) {
            this.captured = event;
        }

        @Override
        public void recordCreate(String m, String t, String i, String d, Object a) {}

        @Override
        public void recordUpdate(String m, String t, String i, String d, Object b, Object a) {}

        @Override
        public void recordDelete(String m, String t, String i, String d, Object b) {}

        @Override
        public void recordLogin(LoginAuditEvent event) {}
    }
}
