package com.wensaas.core.impl.tenant.selfservice;

import com.wensaas.core.api.auth.permission.UserRoleQueryService;
import com.wensaas.core.api.auth.permission.UserRoleQueryService.RoleBrief;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserQueryService;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.compliance.ComplianceService;
import com.wensaas.core.api.compliance.export.ExportRequestPage;
import com.wensaas.core.api.compliance.export.ExportRequestStatus;
import com.wensaas.core.api.notification.inbox.InboxItem;
import com.wensaas.core.api.notification.inbox.InboxPage;
import com.wensaas.core.api.notification.inbox.MessageInboxService;
import com.wensaas.core.api.notification.inbox.UnreadSummary;
import com.wensaas.core.api.spi.DashboardCardProvider;
import com.wensaas.core.api.spi.def.DashboardCardData;
import com.wensaas.core.api.spi.def.DashboardCardDef;
import com.wensaas.core.api.tenant.selfservice.TenantWorkbenchService;
import com.wensaas.core.api.ticket.TicketPage;
import com.wensaas.core.api.ticket.TicketService;
import com.wensaas.core.api.ticket.TicketStatus;
import com.wensaas.core.impl.pkg.TenantPlansResolver;
import com.wensaas.core.impl.spi.registry.DashboardCardRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;

/**
 * 租户工作台聚合实现。
 *
 * <p>不跨租户：所有查询走当前租户线（拦截器自动注入 tenant_id）。
 * 套餐到期块仅主管理员可见；业务数据卡片按可见角色过滤后逐个 provider 求值（fail-soft）。
 */
public class TenantWorkbenchServiceImpl implements TenantWorkbenchService {

    private static final ZoneId TZ = ZoneId.of("Asia/Shanghai");
    private static final int RECENT_MESSAGES = 3;

    private final UserQueryService userQuery;
    private final UserRoleQueryService userRoleQuery;
    private final SysTenantMapper tenantMapper;
    private final TenantPlansResolver plansResolver;
    private final TicketService ticketService;
    private final ObjectProvider<MessageInboxService> messageInboxProvider;
    private final ObjectProvider<ComplianceService> complianceProvider;
    private final DashboardCardRegistry cardRegistry;
    private final ApplicationContext applicationContext;

    public TenantWorkbenchServiceImpl(UserQueryService userQuery,
                                      UserRoleQueryService userRoleQuery,
                                      SysTenantMapper tenantMapper,
                                      TenantPlansResolver plansResolver,
                                      TicketService ticketService,
                                      ObjectProvider<MessageInboxService> messageInboxProvider,
                                      ObjectProvider<ComplianceService> complianceProvider,
                                      DashboardCardRegistry cardRegistry,
                                      ApplicationContext applicationContext) {
        this.userQuery = userQuery;
        this.userRoleQuery = userRoleQuery;
        this.tenantMapper = tenantMapper;
        this.plansResolver = plansResolver;
        this.ticketService = ticketService;
        this.messageInboxProvider = messageInboxProvider;
        this.complianceProvider = complianceProvider;
        this.cardRegistry = cardRegistry;
        this.applicationContext = applicationContext;
    }

    @Override
    public OverviewView overview() {
        UserPrincipal me = UserContext.require();
        List<RoleBrief> roles = loadRoles(me.tenantId(), me.userId());
        return new OverviewView(
                greeting(me, roles),
                planExpiry(me),
                todo(me),
                message(),
                announcement());
    }

    @Override
    public List<BusinessCardView> businessCards() {
        UserPrincipal me = UserContext.require();
        Set<String> myRoleCodes = new HashSet<>();
        for (RoleBrief r : loadRoles(me.tenantId(), me.userId())) {
            myRoleCodes.add(r.code());
        }
        List<DashboardCardDef> defs = new ArrayList<>(cardRegistry.getAll());
        defs.sort(java.util.Comparator.comparingInt(DashboardCardDef::sortWeight));

        List<BusinessCardView> out = new ArrayList<>(defs.size());
        for (DashboardCardDef def : defs) {
            if (!visibleTo(def, me, myRoleCodes)) {
                continue;
            }
            DashboardCardData data = renderCard(def);
            out.add(new BusinessCardView(
                    def.code(),
                    cardRegistry.findOwnerModule(def.code()).orElse(null),
                    def.name(),
                    def.display().name(),
                    def.span().name(),
                    def.actionUrl(),
                    def.sortWeight(),
                    def.showOnH5(),
                    data));
        }
        return out;
    }

    // ===== 固定区域 =====

    private Greeting greeting(UserPrincipal me, List<RoleBrief> roles) {
        String name = userQuery.realName(me.userId());
        if (name == null) {
            name = me.username();
        }
        String roleName;
        if (me.tenantOwner()) {
            roleName = "主管理员";
        } else if (!roles.isEmpty()) {
            roleName = roles.get(0).name();
        } else {
            roleName = "成员";
        }
        return new Greeting(name, roleName);
    }

    /** 套餐到期提醒：仅主管理员可见。 */
    private PlanExpiry planExpiry(UserPrincipal me) {
        if (!me.tenantOwner()) {
            return null;
        }
        SysTenant t = tenantMapper.selectById(me.tenantId());
        if (t == null || t.getExpireAt() == null) {
            return null;
        }
        long daysLeft = Duration.between(Instant.now(), t.getExpireAt()).toDays();
        String severity = daysLeft <= 3 ? "red" : (daysLeft <= 7 ? "yellow" : "none");
        String planName = plansResolver.resolve(me.tenantId()).packageViews().stream()
                .map(com.wensaas.core.api.auth.user.CapabilitiesService.PackageView::name)
                .filter(n -> n != null && !n.isBlank())
                .findFirst().orElse("套餐");
        return new PlanExpiry(planName, t.getExpireAt(), daysLeft, severity);
    }

    /** 我的待办：待确认工单 + （主管理员）待审批导出。 */
    private TodoCard todo(UserPrincipal me) {
        List<TodoEntry> items = new ArrayList<>();

        TicketPage resolved = ticketService.myTickets(TicketStatus.RESOLVED, 1, 1);
        if (resolved.total() > 0) {
            items.add(new TodoEntry("待确认工单", resolved.total(), "/tickets"));
        }

        if (me.tenantOwner()) {
            ComplianceService compliance = complianceProvider.getIfAvailable();
            if (compliance != null) {
                try {
                    ExportRequestPage pending =
                            compliance.listRequests(ExportRequestStatus.PENDING, false, 1, 1);
                    if (pending.total() > 0) {
                        items.add(new TodoEntry("待审批导出", pending.total(), "/compliance"));
                    }
                } catch (RuntimeException ignored) {
                    // 合规模块不可用时忽略，不拖垮工作台
                }
            }
        }

        long total = items.stream().mapToLong(TodoEntry::count).sum();
        return new TodoCard(total, items);
    }

    /** 我的消息：未读总数 + 最近 N 条。通知模块未启用时返回空卡。 */
    private MessageCard message() {
        MessageInboxService inbox = messageInboxProvider.getIfAvailable();
        if (inbox == null) {
            return new MessageCard(0, List.of());
        }
        UnreadSummary summary = inbox.unreadSummary();
        InboxPage page = inbox.list(null, "ALL", null, false, 1, RECENT_MESSAGES);
        List<MessageBrief> recent = new ArrayList<>(page.records().size());
        for (InboxItem it : page.records()) {
            recent.add(new MessageBrief(it.id(), it.title(), it.category(), it.createdAt()));
        }
        return new MessageCard(summary.total(), recent);
    }

    /** 系统公告：最新一条 SYSTEM 类站内信。 */
    private AnnouncementCard announcement() {
        MessageInboxService inbox = messageInboxProvider.getIfAvailable();
        if (inbox == null) {
            return new AnnouncementCard(0, null);
        }
        InboxPage page = inbox.list("SYSTEM", "ALL", null, false, 1, 1);
        AnnouncementBrief latest = null;
        if (!page.records().isEmpty()) {
            InboxItem it = page.records().get(0);
            String from = it.sourceModule() != null && !it.sourceModule().isBlank()
                    ? it.sourceModule() : "平台公告";
            latest = new AnnouncementBrief(it.title(), from, it.createdAt());
        }
        return new AnnouncementCard(page.total(), latest);
    }

    // ===== 业务卡片 =====

    private boolean visibleTo(DashboardCardDef def, UserPrincipal me, Set<String> myRoleCodes) {
        if (me.tenantOwner() || def.visibleRoles().isEmpty()) {
            return true;
        }
        return def.visibleRoles().stream().anyMatch(myRoleCodes::contains);
    }

    private DashboardCardData renderCard(DashboardCardDef def) {
        try {
            DashboardCardProvider provider = applicationContext.getBean(def.providerClass());
            return provider.render();
        } catch (RuntimeException e) {
            // fail-soft：单卡 provider 异常降级为空态，不影响整页
            return null;
        }
    }

    private List<RoleBrief> loadRoles(long tenantId, long userId) {
        return userRoleQuery.rolesOfUser(tenantId, userId);
    }
}
