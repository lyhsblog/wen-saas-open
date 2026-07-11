package com.wensaas.core.impl.auth.impersonate;

import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;
import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.api.auth.login.ImpersonateException;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.ops.access.OpsAccessContext;
import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.ops.access.OpsDataScope;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.auth.org.DeptQueryService;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.login.AuthService;
import com.wensaas.core.impl.auth.session.JwtTokenProvider;
import com.wensaas.core.impl.auth.session.Session;
import com.wensaas.core.impl.auth.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 代登录服务实现。
 *
 * <p>类级 {@link CrossTenantQuery} 保证所有方法执行期间 MyBatis-Plus TenantLineInnerInterceptor
 * 跳过 tenant_id 注入，以便跨租户查询 sys_user / sys_tenant。
 */
@CrossTenantQuery("ImpersonateServiceImpl: 代登录需跨租户查询 sys_user / sys_tenant")
public class ImpersonateServiceImpl implements ImpersonateService {

    private static final Logger log = LoggerFactory.getLogger(ImpersonateServiceImpl.class);

    private final SessionStore sessionStore;
    private final ImpersonateNonceStore nonceStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserMapper userMapper;
    private final TenantQueryService tenantQuery;
    private final DeptQueryService deptQuery;
    private final OpsAccessContextResolver accessResolver;
    private final AuditLogRecorder auditLogRecorder;
    /** 站内信发送（X4-01 代登录知情提醒）；通知模块缺位时为 null，留痕降级为仅审计。 */
    private final MessageService messageService;

    private static final DateTimeFormatter NOTICE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public ImpersonateServiceImpl(SessionStore sessionStore,
                                   ImpersonateNonceStore nonceStore,
                                   JwtTokenProvider jwtTokenProvider,
                                   SysUserMapper userMapper,
                                   TenantQueryService tenantQuery,
                                   DeptQueryService deptQuery,
                                   OpsAccessContextResolver accessResolver,
                                   AuditLogRecorder auditLogRecorder,
                                   MessageService messageService) {
        this.sessionStore = sessionStore;
        this.nonceStore = nonceStore;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
        this.tenantQuery = tenantQuery;
        this.deptQuery = deptQuery;
        this.accessResolver = accessResolver;
        this.auditLogRecorder = auditLogRecorder;
        this.messageService = messageService;
    }

    @Override
    public NonceIssued startImpersonation(long tenantId, long asUserId) {
        // 防嵌套代登录（OpsAuthWebFilter 已拦截非运营请求，此处做深度防御）
        UserPrincipal ops = UserContext.require();
        Session opsSession = sessionStore.findBySessionId(ops.sessionId())
                .orElseThrow(() -> new com.wensaas.core.api.auth.login.AuthenticationException(
                        com.wensaas.core.api.auth.login.AuthenticationException.Code.NO_SESSION, "会话已过期"));
        if (opsSession.originalSessionId() != null) {
            throw new ImpersonateException(
                    ImpersonateException.Code.IMPERSONATE_NESTED_NOT_ALLOWED, "代登录中不允许再次代登录");
        }

        // 验证目标租户
        TenantInfo tenant = tenantQuery.tenantById(tenantId);
        if (tenant == null) {
            throw new ImpersonateException(
                    ImpersonateException.Code.IMPERSONATE_TARGET_TENANT_NOT_FOUND,
                    "目标租户不存在：" + tenantId);
        }
        // 数据范围写守卫：OPS_OWNED（客户经理）仅可代登录 csm_user_id=自己的租户；
        // 放在租户状态校验之前，避免向非名下操作人泄露租户启停状态
        requireScope(tenant);
        if (tenant.status() == null || tenant.status() != 1
                || (tenant.expireAt() != null && tenant.expireAt().isBefore(java.time.Instant.now()))) {
            throw new ImpersonateException(
                    ImpersonateException.Code.IMPERSONATE_TARGET_TENANT_INACTIVE,
                    "目标租户已停用或已过期：" + tenantId);
        }

        // 验证目标用户
        SysUser user = userMapper.selectById(asUserId);
        if (user == null) {
            throw new ImpersonateException(
                    ImpersonateException.Code.IMPERSONATE_TARGET_USER_NOT_FOUND,
                    "目标用户不存在：" + asUserId);
        }
        if (!Objects.equals(user.getTenantId(), tenantId)) {
            throw new ImpersonateException(
                    ImpersonateException.Code.IMPERSONATE_USER_NOT_IN_TENANT,
                    "目标用户不属于该租户：userId=" + asUserId + " tenantId=" + tenantId);
        }
        if (user.getTenantId() == 0L) {
            throw new ImpersonateException(
                    ImpersonateException.Code.IMPERSONATE_TARGET_IS_OPS,
                    "不允许代登录运营账号");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new ImpersonateException(
                    ImpersonateException.Code.IMPERSONATE_TARGET_USER_DISABLED,
                    "目标用户已停用：" + asUserId);
        }

        // 登记审计主对象友好名（落 object_refs，object_desc 由其派生）：租户 + 被代登录成员。
        // 本方法在 @Auditable 控制器方法调用栈内，AuditObjects 由切面收尾 drain。
        AuditObjects.add(EntityTypes.SYS_TENANT, String.valueOf(tenantId), tenant.name());
        String memberName = (user.getRealName() != null && !user.getRealName().isBlank())
                ? user.getRealName() : user.getUsername();
        AuditObjects.add(EntityTypes.SYS_USER, String.valueOf(asUserId), memberName);

        // 签发 nonce
        ImpersonateNonceData nonceData = new ImpersonateNonceData(
                tenantId, asUserId, ops.userId(), ops.sessionId(), Instant.now());
        String nonce = nonceStore.issue(nonceData);

        log.info("impersonate.nonce.issued operatorUserId={} tenantId={} asUserId={}",
                ops.userId(), tenantId, asUserId);

        // 知情提醒（X4-01）：向租户主管理员发系统站内信留痕。best-effort，失败不阻断代登录。
        notifyTenantMainAdmin(tenant, ops);

        return new NonceIssued(nonce, tenant.code(), 300L);
    }

    /** 运营数据范围守卫：OPS_OWNED 代登录非名下租户 → 403（与租户管理写路径同语义）。 */
    private void requireScope(TenantInfo tenant) {
        OpsAccessContext ctx = accessResolver.require();
        if (ctx.dataScope() != OpsDataScope.OPS_OWNED) {
            return;
        }
        if (tenant.csmUserId() == null || tenant.csmUserId() != ctx.userId()) {
            throw new ImpersonateException(
                    ImpersonateException.Code.IMPERSONATE_TENANT_NOT_IN_SCOPE,
                    "无权代登录该租户（非名下客户）：" + tenant.id());
        }
    }

    /** 代登录开始时向租户主管理员发系统站内信（X4-01）。需在目标租户上下文内发送以正确落该租户站内信。 */
    private void notifyTenantMainAdmin(TenantInfo tenant, UserPrincipal ops) {
        if (messageService == null || tenant.mainAdminUserId() == null) {
            return;
        }
        try {
            String operatorName = (ops.realName() != null && !ops.realName().isBlank())
                    ? ops.realName() : ops.username();
            long adminUserId = tenant.mainAdminUserId();
            long tenantId = tenant.id();
            MessageSendRequest req = MessageSendRequest.builder("sys.security.impersonate_started")
                    .variables(Map.of(
                            "operator_name", operatorName,
                            "start_time", NOTICE_TIME_FMT.format(Instant.now())))
                    .recipients(List.of(RecipientSpec.member(adminUserId)))
                    .channels(List.of(Channel.STATION))
                    .category(MessageCategory.SYSTEM)
                    .priority(com.wensaas.core.api.notification.send.Priority.IMPORTANT)
                    .build();
            // send() 用 TenantContext.require() 决定模板/站内信 tenant_id 与收件人解析；本类
            // @CrossTenantQuery 上下文为运营(0)，故切到目标租户上下文执行。
            TenantContext.runAs(tenantId, () -> messageService.send(req));
        } catch (RuntimeException e) {
            log.warn("impersonate.notify.fail tenantId={} reason={}", tenant.id(), e.getMessage());
        }
    }

    @Override
    public AuthService.LoginResult redeemNonce(String nonce) {
        ImpersonateNonceData data = nonceStore.redeem(nonce)
                .orElseThrow(() -> new ImpersonateException(
                        ImpersonateException.Code.IMPERSONATE_NONCE_INVALID, "nonce 无效或已过期"));

        // 验证运营原 session 仍在 Redis（同时取运营操作人登录名，快照进代登录 session 供前端水印溯源）
        Session opsSession = sessionStore.findBySessionId(data.operatorSessionId())
                .orElseThrow(() -> new ImpersonateException(
                        ImpersonateException.Code.IMPERSONATE_OPERATOR_SESSION_GONE,
                        "运营原会话已过期，请重新登录后再发起代登录"));

        // 加载目标用户（@CrossTenantQuery 在接口方法上，TenantLine 已跳过）
        SysUser user = userMapper.selectById(data.asUserId());
        if (user == null) {
            // 极罕见：nonce 签发后用户被删除
            throw new ImpersonateException(
                    ImpersonateException.Code.IMPERSONATE_TARGET_USER_NOT_FOUND,
                    "目标用户已不存在：" + data.asUserId());
        }
        boolean tenantOwner = resolveTenantOwner(user);
        Long mainDeptId = resolveMainDeptId(user);
        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                user.getId(), user.getTenantId(), user.getUsername(),
                tenantOwner, mainDeptId, false, null, user.getRealName());

        // 创建代登录 session（含 originalSessionId 反向链 + 运营操作人名快照）
        String impersonateSessionId = sessionStore.createImpersonatedSession(
                identity, data.operatorSessionId(), opsSession.username());
        String token = jwtTokenProvider.issue(impersonateSessionId);

        UserPrincipal principal = new UserPrincipal(
                user.getId(), user.getTenantId(), user.getUsername(), impersonateSessionId,
                tenantOwner, mainDeptId, false, null, user.getRealName());

        log.info("impersonate.nonce.redeemed tenantId={} asUserId={} impersonateSessionId={}",
                data.targetTenantId(), data.asUserId(), impersonateSessionId);
        return AuthService.LoginResult.completed(token, jwtTokenProvider.tokenTtl(), principal);
    }

    @Override
    public void exitImpersonation(String currentSessionId) {
        Session cur = sessionStore.findBySessionId(currentSessionId)
                .orElseThrow(() -> new com.wensaas.core.api.auth.login.AuthenticationException(
                        com.wensaas.core.api.auth.login.AuthenticationException.Code.NO_SESSION, "会话已过期"));

        if (cur.originalSessionId() == null) {
            throw new ImpersonateException(
                    ImpersonateException.Code.EXIT_IMPERSONATION_NOT_IMPERSONATING,
                    "当前不处于代登录状态");
        }

        // 查 originalSessionId 仅用于审计溯源（取运营 userId / username）；
        // 运营原 session 在另一域名独立存活，本端不重签 token、不动它的生命周期。
        Session orig = sessionStore.findBySessionId(cur.originalSessionId())
                .orElseThrow(() -> new ImpersonateException(
                        ImpersonateException.Code.EXIT_IMPERSONATION_ORIGINAL_GONE,
                        "运营原会话已过期，请重新登录"));

        sessionStore.invalidate(currentSessionId);

        // 手动记录审计（无法用 @Auditable：当前 UserContext 是被代登录用户，tenant_id 非 0）
        recordExitAudit(cur, orig);

        log.info("impersonate.exit operatorUserId={} targetTenantId={} impersonateSessionId={}",
                orig.userId(), cur.tenantId(), currentSessionId);
    }

    private void recordExitAudit(Session impersonateSession, Session origOpsSession) {
        try {
            // 对象友好名=租户名（手动事件无 @Auditable/AuditObjects，直接写 object_desc，与其它租户行口径一致）
            TenantInfo tenant = tenantQuery.tenantById(impersonateSession.tenantId());
            String tenantName = tenant != null ? tenant.name() : null;
            auditLogRecorder.record(new AuditEvent(
                    null,
                    0L,                                          // tenant_id=0（运营方）
                    impersonateSession.tenantId(),               // target_tenant_id
                    requestId(),
                    origOpsSession.userId(),                     // operator_id（运营用户）
                    operatorDisplayName(origOpsSession),         // operator_name：realName 优先，空回退账号（与 @Auditable 口径一致）
                    origOpsSession.mainDeptId(),
                    "__core__",
                    "退出代登录",
                    EntityTypes.SYS_TENANT,
                    String.valueOf(impersonateSession.tenantId()),
                    tenantName,                                  // object_desc=租户名
                    null, null,                                  // method, path
                    200, 0,                                      // status_code, duration_ms
                    null, null, null,                            // error_message, ip, user_agent
                    Instant.now()));
        } catch (RuntimeException e) {
            log.warn("impersonate.exit.audit.fail reason={}", e.getMessage());
        }
    }

    /** 操作人展示名：realName 优先、空回退账号（与 {@code AuditableAspect.currentOperatorName()} 同口径）。 */
    private static String operatorDisplayName(Session s) {
        return (s.realName() != null && !s.realName().isBlank()) ? s.realName() : s.username();
    }

    private boolean resolveTenantOwner(SysUser user) {
        if (user.getTenantId() == null || user.getTenantId() == 0L) {
            return false;
        }
        TenantInfo tenant = tenantQuery.tenantById(user.getTenantId());
        return tenant != null && Objects.equals(tenant.mainAdminUserId(), user.getId());
    }

    private Long resolveMainDeptId(SysUser user) {
        return deptQuery.mainDeptIdOfUser(user.getId());
    }

    private static String requestId() {
        String rid = RequestContext.get();
        return rid != null && !rid.isBlank() ? rid : "system";
    }
}
