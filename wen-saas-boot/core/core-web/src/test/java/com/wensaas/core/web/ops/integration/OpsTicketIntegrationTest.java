package com.wensaas.core.web.ops.integration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.api.ticket.TicketPriority;
import com.wensaas.core.api.ticket.TicketStatus;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import com.wensaas.core.impl.auth.password.BCryptPasswordHasher;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.ops.OpsAuthWebFilter;
import com.wensaas.core.impl.permission.role.SysUserRole;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.ticket.TicketMessage;
import com.wensaas.core.impl.ticket.TicketMessageMapper;
import com.wensaas.core.impl.ticket.TicketTicket;
import com.wensaas.core.impl.ticket.TicketTicketMapper;
import com.wensaas.core.impl.tenant.web.TenantContextWebFilter;
import com.wensaas.core.web.auth.integration.WebTestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运营工单管理集成测试。
 *
 * <p>覆盖：列表 + 认领/回复/标记已解决状态机 + 非法状态冲突 + 权限守卫（CSM 无 ops.ticket.reopen，
 * 不可强制重启）+ 数据范围（CSM 仅看 csm_user_id=self 的租户）+ 404。工单行经 mapper 直接注入（生产由租户提交）。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(OpsTicketIntegrationTest.TestFixtures.class)
class OpsTicketIntegrationTest {

    private static final long ROLE_SUPER_ADMIN = 12100001L;
    private static final long ROLE_CSM = 12100002L;

    private static final String PASSWORD = "Admin@2026!";
    private static final String ADMIN_USER = "ops-ticket-it-admin";
    private static final String CSM_USER = "ops-ticket-it-csm";
    private static final String TENANT_PREFIX = "OPSTICKET_IT_";
    private static final String TICKET_PREFIX = "T-OPSITX-";

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationFilter authenticationFilter;
    @Autowired private TenantContextWebFilter tenantContextWebFilter;
    @Autowired private OpsAuthWebFilter opsAuthWebFilter;
    @Autowired private TestFixtures fixtures;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(authenticationFilter, tenantContextWebFilter, opsAuthWebFilter)
                .build();
        fixtures.cleanup();
        fixtures.seedOpsUser(ADMIN_USER, ROLE_SUPER_ADMIN);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void noToken_unauthorized() throws Exception {
        mvc.perform(get("/api/v1/ops/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_superAdmin_returns200() throws Exception {
        long tenant = fixtures.seedTenant(TENANT_PREFIX + "A", 0L);
        fixtures.seedTicket(tenant, 1001L, TicketStatus.PENDING);
        String token = login(ADMIN_USER);
        mvc.perform(get("/api/v1/ops/tickets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").isArray())
                .andExpect(jsonPath("$.total").exists());
    }

    @Test
    void claim_reply_resolve_flow() throws Exception {
        long tenant = fixtures.seedTenant(TENANT_PREFIX + "B", 0L);
        long ticketId = fixtures.seedTicket(tenant, 1001L, TicketStatus.PENDING);
        String token = login(ADMIN_USER);

        mvc.perform(post("/api/v1/ops/tickets/" + ticketId + "/claim")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/ops/tickets/" + ticketId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("PROCESSING"))
                .andExpect(jsonPath("$.summary.assigneeId").exists());

        mvc.perform(post("/api/v1/ops/tickets/" + ticketId + "/reply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("content", "请提供登录账号"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorSide").value("OPS"));

        mvc.perform(post("/api/v1/ops/tickets/" + ticketId + "/resolve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/ops/tickets/" + ticketId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());
    }

    @Test
    void claim_alreadyProcessing_returns409() throws Exception {
        long tenant = fixtures.seedTenant(TENANT_PREFIX + "C", 0L);
        long ticketId = fixtures.seedTicket(tenant, 1001L, TicketStatus.PROCESSING);
        String token = login(ADMIN_USER);
        mvc.perform(post("/api/v1/ops/tickets/" + ticketId + "/claim")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
    }

    @Test
    void detail_unknownTicket_returns404() throws Exception {
        String token = login(ADMIN_USER);
        mvc.perform(get("/api/v1/ops/tickets/99887766").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    @Test
    void csm_scopedToOwnedTenants() throws Exception {
        long csmUserId = fixtures.seedOpsUser(CSM_USER, ROLE_CSM);
        long ownTenant = fixtures.seedTenant(TENANT_PREFIX + "OWN", csmUserId);
        long otherTenant = fixtures.seedTenant(TENANT_PREFIX + "OTHER", 99999999L);
        long ownTicket = fixtures.seedTicket(ownTenant, 1001L, TicketStatus.PENDING);
        long otherTicket = fixtures.seedTicket(otherTenant, 1002L, TicketStatus.PENDING);

        String token = login(CSM_USER);
        mvc.perform(get("/api/v1/ops/tickets/" + ownTicket).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/ops/tickets/" + otherTicket).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void superAdmin_reopen_clearsClosedFields() throws Exception {
        long tenant = fixtures.seedTenant(TENANT_PREFIX + "REOPEN", 0L);
        long ticketId = fixtures.seedTicket(tenant, 1001L, TicketStatus.CLOSED);
        fixtures.setCloseReason(ticketId, "已解决 7 天未回应，系统自动关闭");
        String token = login(ADMIN_USER);

        mvc.perform(post("/api/v1/ops/tickets/" + ticketId + "/reopen")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 重启后必须把 closed/resolved/closeReason 清回 null（updateById NOT_NULL 策略坑：
        // 字段需 @TableField(updateStrategy = ALWAYS) 才会写 null）。
        TicketTicket reloaded = fixtures.reload(ticketId);
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.PROCESSING.dbValue());
        assertThat(reloaded.getClosedAt()).isNull();
        assertThat(reloaded.getResolvedAt()).isNull();
        assertThat(reloaded.getCloseReason()).isNull();
    }

    @Test
    void csm_cannotForceReopen_returns403() throws Exception {
        // CSM 白名单只有 ops.ticket.view / .handle，无 ops.ticket.reopen → 403 在 @RequirePermission 处被拦
        fixtures.seedOpsUser(CSM_USER, ROLE_CSM);
        long tenant = fixtures.seedTenant(TENANT_PREFIX + "D", 0L);
        long ticketId = fixtures.seedTicket(tenant, 1001L, TicketStatus.CLOSED);
        String token = login(CSM_USER);
        mvc.perform(post("/api/v1/ops/tickets/" + ticketId + "/reopen")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String login(String username) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
    }

    @Component
    @CrossTenantQuery("OpsTicketIT: test fixture")
    static class TestFixtures {

        private static final AtomicInteger SEQ = new AtomicInteger(0);

        final SysUserMapper userMapper;
        final SysUserRoleMapper userRoleMapper;
        final SysTenantMapper tenantMapper;
        final TicketTicketMapper ticketMapper;
        final TicketMessageMapper messageMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                     SysTenantMapper tenantMapper, TicketTicketMapper ticketMapper,
                     TicketMessageMapper messageMapper) {
            this.userMapper = userMapper;
            this.userRoleMapper = userRoleMapper;
            this.tenantMapper = tenantMapper;
            this.ticketMapper = ticketMapper;
            this.messageMapper = messageMapper;
        }

        long seedOpsUser(String username, long roleId) {
            SysUser u = new SysUser();
            u.setTenantId(0L);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(PASSWORD));
            u.setRealName(username);
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);

            SysUserRole ur = new SysUserRole();
            ur.setTenantId(0L);
            ur.setUserId(u.getId());
            ur.setRoleId(roleId);
            ur.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(ur);

            return u.getId();
        }

        long seedTenant(String code, long csmUserId) {
            SysTenant t = new SysTenant();
            t.setCode(code);
            t.setName(code);
            t.setStatus(1);
            t.setCsmUserId(csmUserId == 0L ? null : csmUserId);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            tenantMapper.insert(t);
            return t.getId();
        }

        long seedTicket(long tenantId, long submitterId, TicketStatus status) {
            Instant now = Instant.now();
            TicketTicket t = new TicketTicket();
            t.setTenantId(tenantId);
            t.setTicketNo(TICKET_PREFIX + SEQ.incrementAndGet());
            t.setTitle("登录失败");
            t.setType(0);
            t.setPriority(TicketPriority.NORMAL.dbValue());
            t.setStatus(status.dbValue());
            t.setSubmitterId(submitterId);
            t.setSubmitterName("王经理");
            t.setSlaDueAt(now.plus(72, ChronoUnit.HOURS));
            t.setSlaWarned(false);
            t.setSlaBreached(false);
            if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
                t.setResolvedAt(now);
            }
            if (status == TicketStatus.CLOSED) {
                t.setClosedAt(now);
            }
            t.setLastActivityAt(now);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            ticketMapper.insert(t);

            TicketMessage m = new TicketMessage();
            m.setTenantId(tenantId);
            m.setTicketId(t.getId());
            m.setAuthorId(submitterId);
            m.setAuthorName("王经理");
            m.setAuthorSide(0);
            m.setContent("客户登录失败，截图见附件");
            m.setCreatedAt(LocalDateTime.now());
            messageMapper.insert(m);
            return t.getId();
        }

        void setCloseReason(long ticketId, String reason) {
            TicketTicket t = ticketMapper.selectById(ticketId);
            t.setCloseReason(reason);
            ticketMapper.updateById(t);
        }

        TicketTicket reload(long ticketId) {
            return ticketMapper.selectById(ticketId);
        }

        void cleanup() {
            List<TicketTicket> tickets = ticketMapper.selectList(Wrappers.<TicketTicket>lambdaQuery()
                    .likeRight(TicketTicket::getTicketNo, TICKET_PREFIX));
            List<Long> ids = new ArrayList<>();
            for (TicketTicket t : tickets) {
                ids.add(t.getId());
            }
            if (!ids.isEmpty()) {
                messageMapper.delete(Wrappers.<TicketMessage>lambdaQuery().in(TicketMessage::getTicketId, ids));
                ticketMapper.delete(Wrappers.<TicketTicket>lambdaQuery().in(TicketTicket::getId, ids));
            }
            for (String username : List.of(ADMIN_USER, CSM_USER)) {
                SysUser u = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getTenantId, 0L)
                        .eq(SysUser::getUsername, username));
                if (u != null) {
                    userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                            .eq(SysUserRole::getUserId, u.getId()));
                    userMapper.deleteById(u.getId());
                }
            }
            tenantMapper.delete(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
        }
    }
}
