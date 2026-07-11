package com.wensaas.core.web.ticket.integration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import com.wensaas.core.impl.auth.password.BCryptPasswordHasher;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.ops.OpsAuthWebFilter;
import com.wensaas.core.impl.permission.role.SysUserRole;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.ticket.TicketAttachment;
import com.wensaas.core.impl.ticket.TicketAttachmentMapper;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 租户「我的工单」集成测试。
 *
 * <p>覆盖：提交 → 查我的列表 → 详情 → 运营认领/回复/解决 → 用户确认关闭的端到端闭环；
 * 以及 {@code base.ticket.view_all} 守卫（非主管成员无权访问本租户全部工单）。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(TenantTicketIntegrationTest.TestFixtures.class)
class TenantTicketIntegrationTest {

    private static final long ROLE_SUPER_ADMIN = 12100001L;
    private static final String PASSWORD = "Admin@2026!";
    private static final String OPS_USER = "tenant-ticket-it-ops";
    private static final String OWNER_USER = "tenant-ticket-it-owner";
    private static final String MEMBER_USER = "tenant-ticket-it-member";
    private static final String TENANT_PREFIX = "TENTICKET_IT_";

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
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void submit_handle_confirm_fullLoop() throws Exception {
        String tenantCode = TENANT_PREFIX + "A";
        long tenant = fixtures.seedTenant(tenantCode);
        fixtures.seedTenantUser(OWNER_USER, tenant, true);
        fixtures.seedOpsUser(OPS_USER, ROLE_SUPER_ADMIN);

        String memberToken = login(OWNER_USER, tenantCode);

        // 提交工单
        MvcResult submit = mvc.perform(post("/api/v1/base/tickets")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of(
                                "title", "登录失败",
                                "type", "BUG",
                                "priority", "URGENT",
                                "content", "客户登录失败，请协助排查"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.ticketNo").exists())
                .andExpect(jsonPath("$.summary.status").value("PENDING"))
                .andExpect(jsonPath("$.messages[0].content").value("客户登录失败，请协助排查"))
                .andReturn();
        String ticketId = JSON.parseObject(submit.getResponse().getContentAsString())
                .getJSONObject("summary").getString("id");

        // 我的工单列表包含它
        mvc.perform(get("/api/v1/base/tickets").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("1"));

        // 运营认领 + 回复 + 标记已解决
        String opsToken = login(OPS_USER);
        mvc.perform(post("/api/v1/ops/tickets/" + ticketId + "/claim")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/ops/tickets/" + ticketId + "/reply")
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("content", "已定位，请重试"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/ops/tickets/" + ticketId + "/resolve")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isNoContent());

        // 租户侧详情可见客服回复 + 已解决
        mvc.perform(get("/api/v1/base/tickets/" + ticketId).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("RESOLVED"))
                .andExpect(jsonPath("$.messages[1].authorSide").value("OPS"));

        // 用户确认关闭
        mvc.perform(post("/api/v1/base/tickets/" + ticketId + "/confirm")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/base/tickets/" + ticketId).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("CLOSED"));
    }

    @Test
    void tenantAll_withoutPermission_returns403() throws Exception {
        String tenantCode = TENANT_PREFIX + "B";
        long tenant = fixtures.seedTenant(tenantCode);
        fixtures.seedTenantUser(OWNER_USER, tenant, true);
        fixtures.seedTenantUser(MEMBER_USER, tenant, false);

        String memberToken = login(MEMBER_USER, tenantCode);
        mvc.perform(get("/api/v1/base/tickets/all").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    /** 运营账号登录（tenant_id=0，无 tenantCode）。 */
    private String login(String username) throws Exception {
        return login(username, null);
    }

    /** 租户成员登录需带 tenantCode（v1 按 (tenant_id, username) 解析）。 */
    private String login(String username, String tenantCode) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("username", username);
        body.put("password", PASSWORD);
        if (tenantCode != null) {
            body.put("tenantCode", tenantCode);
        }
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
    }

    @Component
    @CrossTenantQuery("TenantTicketIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysUserRoleMapper userRoleMapper;
        final SysTenantMapper tenantMapper;
        final TicketTicketMapper ticketMapper;
        final TicketMessageMapper messageMapper;
        final TicketAttachmentMapper attachmentMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                     SysTenantMapper tenantMapper, TicketTicketMapper ticketMapper,
                     TicketMessageMapper messageMapper, TicketAttachmentMapper attachmentMapper) {
            this.userMapper = userMapper;
            this.userRoleMapper = userRoleMapper;
            this.tenantMapper = tenantMapper;
            this.ticketMapper = ticketMapper;
            this.messageMapper = messageMapper;
            this.attachmentMapper = attachmentMapper;
        }

        long seedTenant(String code) {
            SysTenant t = new SysTenant();
            t.setCode(code);
            t.setName(code);
            t.setStatus(1);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            tenantMapper.insert(t);
            return t.getId();
        }

        long seedTenantUser(String username, long tenantId, boolean asOwner) {
            SysUser u = new SysUser();
            u.setTenantId(tenantId);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(PASSWORD));
            u.setRealName(username);
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);
            if (asOwner) {
                SysTenant patch = new SysTenant();
                patch.setId(tenantId);
                patch.setMainAdminUserId(u.getId());
                tenantMapper.updateById(patch);
            }
            return u.getId();
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

        void cleanup() {
            List<SysTenant> tenants = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
            List<Long> tenantIds = new ArrayList<>();
            for (SysTenant t : tenants) {
                tenantIds.add(t.getId());
            }
            if (!tenantIds.isEmpty()) {
                attachmentMapper.delete(Wrappers.<TicketAttachment>lambdaQuery().in(TicketAttachment::getTenantId, tenantIds));
                messageMapper.delete(Wrappers.<TicketMessage>lambdaQuery().in(TicketMessage::getTenantId, tenantIds));
                ticketMapper.delete(Wrappers.<TicketTicket>lambdaQuery().in(TicketTicket::getTenantId, tenantIds));
            }
            for (String username : List.of(OPS_USER, OWNER_USER, MEMBER_USER)) {
                SysUser u = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
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
