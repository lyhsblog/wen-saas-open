package com.wensaas.core.web.tenant.integration;

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
import com.wensaas.core.impl.tenant.web.TenantContextWebFilter;
import com.wensaas.core.impl.ticket.TicketTicket;
import com.wensaas.core.impl.ticket.TicketTicketMapper;
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
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 租户工作台集成测试。
 *
 * <p>覆盖：① 主管理员 overview —— 欢迎语 / 套餐到期可见 / 待确认工单待办 / 消息公告固定卡（通知模块未启用降级为 0）；
 * ② 业务卡片 SPI —— {@code base.ticket.my_status} 经 provider 实时求值出 3 个分项；
 * ③ 非主管理员 overview —— 套餐到期块收敛为 null。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(TenantWorkbenchIntegrationTest.TestFixtures.class)
class TenantWorkbenchIntegrationTest {

    private static final String PASSWORD = "Admin@2026!";
    private static final String OWNER_USER = "tenant-wb-it-owner";
    private static final String MEMBER_USER = "tenant-wb-it-member";
    private static final String TENANT_PREFIX = "TENWB_IT_";

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
    void ownerOverview_and_businessCards() throws Exception {
        String tenantCode = TENANT_PREFIX + "A";
        long tenant = fixtures.seedTenant(tenantCode, Instant.now().plusSeconds(5 * 86400)); // 5 天后到期
        long ownerId = fixtures.seedOwner(OWNER_USER, tenant);
        fixtures.seedResolvedTicket(tenant, ownerId, OWNER_USER); // 一条已解决工单 → 待确认
        String token = login(OWNER_USER, tenantCode);

        // overview：欢迎语 + 套餐到期（主管理员可见，5 天 → yellow）+ 待确认工单 + 固定消息/公告卡
        mvc.perform(get("/api/v1/base/workbench/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.greeting.userName").value(OWNER_USER))
                .andExpect(jsonPath("$.greeting.roleName").value("主管理员"))
                .andExpect(jsonPath("$.planExpiry.severity").value("yellow")) // 5 天到期 → ≤7 天黄
                .andExpect(jsonPath("$.todo.total").value(1))
                .andExpect(jsonPath("$.todo.items[0].label").value("待确认工单"))
                .andExpect(jsonPath("$.message.unreadTotal").value(0))
                .andExpect(jsonPath("$.announcement.total").value(0));

        // 业务卡片：base.ticket.my_status 经 provider 求值，LIST 含 3 分项，本月解决=1
        mvc.perform(get("/api/v1/base/workbench/cards").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("base.ticket.my_status"))
                .andExpect(jsonPath("$[0].moduleCode").value("base"))
                .andExpect(jsonPath("$[0].display").value("LIST"))
                .andExpect(jsonPath("$[0].span").value("THIRD"))
                .andExpect(jsonPath("$[0].data.items.length()").value(3))
                .andExpect(jsonPath("$[0].data.items[2].label").value("本月解决"))
                .andExpect(jsonPath("$[0].data.items[2].value").value("1"));
    }

    @Test
    void memberOverview_planExpiryHidden() throws Exception {
        String tenantCode = TENANT_PREFIX + "B";
        long tenant = fixtures.seedTenant(tenantCode, Instant.now().plusSeconds(5 * 86400));
        fixtures.seedOwner(OWNER_USER, tenant);            // 主管理员另设
        fixtures.seedMember(MEMBER_USER, tenant);          // 当前登录者非主管理员
        String token = login(MEMBER_USER, tenantCode);

        mvc.perform(get("/api/v1/base/workbench/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.greeting.userName").value(MEMBER_USER))
                .andExpect(jsonPath("$.greeting.roleName").value("成员"))
                .andExpect(jsonPath("$.planExpiry").doesNotExist()); // 非主管理员收敛为 null
    }

    private String login(String username, String tenantCode) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("username", username);
        body.put("password", PASSWORD);
        body.put("tenantCode", tenantCode);
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
    }

    @Component
    @CrossTenantQuery("TenantWorkbenchIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysTenantMapper tenantMapper;
        final TicketTicketMapper ticketMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysTenantMapper tenantMapper, TicketTicketMapper ticketMapper) {
            this.userMapper = userMapper;
            this.tenantMapper = tenantMapper;
            this.ticketMapper = ticketMapper;
        }

        long seedTenant(String code, Instant expireAt) {
            SysTenant t = new SysTenant();
            t.setCode(code);
            t.setName(code);
            t.setStatus(1);
            t.setExpireAt(expireAt);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            tenantMapper.insert(t);
            return t.getId();
        }

        long seedOwner(String username, long tenantId) {
            long id = insertUser(username, tenantId);
            SysTenant patch = new SysTenant();
            patch.setId(tenantId);
            patch.setMainAdminUserId(id);
            tenantMapper.updateById(patch);
            return id;
        }

        long seedMember(String username, long tenantId) {
            return insertUser(username, tenantId);
        }

        private long insertUser(String username, long tenantId) {
            SysUser u = new SysUser();
            u.setTenantId(tenantId);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(PASSWORD));
            u.setRealName(username);
            u.setMobile(null);
            u.setEmail(username + "@example.com");
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);
            return u.getId();
        }

        void seedResolvedTicket(long tenantId, long submitterId, String submitterName) {
            TicketTicket t = new TicketTicket();
            t.setTenantId(tenantId);
            t.setTicketNo("T-WBIT-" + System.nanoTime());
            t.setTitle("工作台测试工单");
            t.setType(1);
            t.setPriority(1);
            t.setStatus(2); // RESOLVED
            t.setSubmitterId(submitterId);
            t.setSubmitterName(submitterName);
            t.setSlaDueAt(Instant.now().plusSeconds(86400));
            t.setSlaWarned(false);
            t.setSlaBreached(false);
            t.setResolvedAt(Instant.now());
            t.setLastActivityAt(Instant.now());
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            ticketMapper.insert(t);
        }

        void cleanup() {
            List<SysTenant> tenants = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
            for (SysTenant t : tenants) {
                userMapper.delete(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getTenantId, t.getId()));
                ticketMapper.delete(Wrappers.<TicketTicket>lambdaQuery().eq(TicketTicket::getTenantId, t.getId()));
            }
            tenantMapper.delete(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
        }
    }
}
