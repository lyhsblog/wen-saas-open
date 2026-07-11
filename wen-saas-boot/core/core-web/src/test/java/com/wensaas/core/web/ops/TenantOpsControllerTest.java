package com.wensaas.core.web.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wensaas.core.api.auth.login.ImpersonateException;
import com.wensaas.core.api.ops.TenantOpsException;
import com.wensaas.core.api.ops.TenantOpsService;
import com.wensaas.core.impl.auth.impersonate.ImpersonateService;
import com.wensaas.core.web.auth.ImpersonateRestExceptionAdvice;
import com.wensaas.core.web.ops.dto.CreateTenantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class TenantOpsControllerTest {

    private TenantOpsService tenantOpsService;
    private ImpersonateService impersonateService;
    private MockMvc mockMvc;
    private ObjectMapper json;

    @BeforeEach
    void setup() {
        tenantOpsService = mock(TenantOpsService.class);
        impersonateService = mock(ImpersonateService.class);
        TenantOpsController controller = new TenantOpsController(tenantOpsService, impersonateService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TenantOpsRestExceptionAdvice(), new ImpersonateRestExceptionAdvice())
                .build();
        json = new ObjectMapper();
    }

    @Test
    void createTenant_happyPath_returns200WithResetToken() throws Exception {
        TenantOpsService.CreatedTenantResult result = new TenantOpsService.CreatedTenantResult(
                9001L, "acmecorp", "T1234ABCD", "admin", "tok-abc-123",
                Instant.now().plusSeconds(172800L)
        );
        when(tenantOpsService.createTenant(any())).thenReturn(result);

        CreateTenantRequest req = new CreateTenantRequest(
                "Acme Corp", "acmecorp", null, "admin", "Admin User",
                "13800138000", "admin@acme.com",
                null, null, null, null, null,
                null, null, null, null, null, null, 0, null, List.of()
        );
        mockMvc.perform(post("/api/v1/ops/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(200);
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"tenantId\":\"9001\"");
                    assertThat(body).contains("\"tenantCode\":\"acmecorp\"");
                    assertThat(body).contains("\"resetToken\":\"tok-abc-123\"");
                });
    }

    @Test
    void createTenant_duplicateTenantCode_returns409() throws Exception {
        when(tenantOpsService.createTenant(any()))
                .thenThrow(new TenantOpsException(
                        TenantOpsException.Code.DUPLICATE_TENANT_CODE, "租户标识已存在：acme"));

        CreateTenantRequest req = new CreateTenantRequest(
                "Acme Corp", "acmecorp", null, "admin", "Admin User", "13800138000", "admin@acme.com", null, null, null, null, null, null, null, null, null, null, null, 0, null, List.of()
        );
        mockMvc.perform(post("/api/v1/ops/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(409);
                    assertThat(r.getResponse().getContentAsString()).contains("DUPLICATE_TENANT_CODE");
                    assertThat(r.getResponse().getContentType()).contains("application/problem+json");
                });
    }

    @Test
    void createTenant_packageNotFound_returns404() throws Exception {
        when(tenantOpsService.createTenant(any()))
                .thenThrow(new TenantOpsException(
                        TenantOpsException.Code.PACKAGE_NOT_FOUND, "base 套餐不存在"));

        CreateTenantRequest req = new CreateTenantRequest(
                "Test Corp", "test-co", null, "testadmin", "Test Admin", "13800138000", "admin@test.co", null, null, null, null, null, null, null, null, null, null, null, 0, null, List.of()
        );
        mockMvc.perform(post("/api/v1/ops/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(404));
    }

    @Test
    void createTenant_missingTenantName_returns400() throws Exception {
        // tenantName is @NotBlank, empty string should fail validation
        CreateTenantRequest req = new CreateTenantRequest(
                "", "acme", null, "admin", null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, null, List.of()
        );
        mockMvc.perform(post("/api/v1/ops/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(400));
    }

    @Test
    void listTenants_emptyResult_returns200() throws Exception {
        TenantOpsService.TenantPage page = new TenantOpsService.TenantPage(
                List.of(), 0L, 1, 20);
        when(tenantOpsService.listTenants(1, 20, null, null, null, null, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/v1/ops/tenants"))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(200);
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"total\":0");
                    assertThat(body).contains("\"items\":[]");
                });
    }

    @Test
    void listTenants_withActivationStatus_returns200() throws Exception {
        TenantOpsService.TenantListItem item = new TenantOpsService.TenantListItem(
                "9001", "acme", "Acme Corp", "T1234ABCD", "admin",
                "1001", "CSM 张三",
                1, "pending_activation", Instant.parse("2026-05-25T10:00:00Z"),
                Instant.parse("2026-05-27T08:00:00Z"),
                2, List.of("base", "crm"), Instant.parse("2026-12-31T16:00:00Z"), 5);
        TenantOpsService.TenantPage page = new TenantOpsService.TenantPage(
                List.of(item), 1L, 1, 20);
        when(tenantOpsService.listTenants(1, 20, null, null, null, null, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/v1/ops/tenants"))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(200);
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"activationStatus\":\"pending_activation\"");
                    assertThat(body).contains("\"adminUsername\":\"admin\"");
                });
    }

    @Test
    void exportTenants_returnsCsv() throws Exception {
        TenantOpsService.TenantListItem item = new TenantOpsService.TenantListItem(
                "9001", "acme", "Acme Corp", "T1234ABCD", "admin",
                "1001", "CSM 张三",
                1, "activated", Instant.parse("2026-05-25T10:00:00Z"),
                Instant.parse("2026-05-27T08:00:00Z"),
                2, List.of("base", "crm"), Instant.parse("2026-12-31T16:00:00Z"), 5);
        TenantOpsService.TenantPage page = new TenantOpsService.TenantPage(List.of(item), 1L, 1, 10000);
        when(tenantOpsService.listTenants(1, 10000, null, null, null, null, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/v1/ops/tenants/export"))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(200);
                    assertThat(r.getResponse().getContentType()).contains("text/csv");
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("租户名,编码");
                    assertThat(body).contains("Acme Corp");
                    assertThat(body).contains("正常");
                });
    }

    @Test
    void reissueResetLink_happyPath_returns200() throws Exception {
        TenantOpsService.IssuedResetLink issued = new TenantOpsService.IssuedResetLink(
                "newtoken-xyz", Instant.parse("2026-05-27T18:30:00Z"), "alice");
        when(tenantOpsService.reissueResetLink(9001L)).thenReturn(issued);

        mockMvc.perform(post("/api/v1/ops/tenants/9001/password-reset-link"))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(200);
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"resetToken\":\"newtoken-xyz\"");
                    assertThat(body).contains("\"adminUsername\":\"alice\"");
                    assertThat(body).contains("+08:00");
                });
    }

    @Test
    void reissueResetLink_tenantNotFound_returns404() throws Exception {
        when(tenantOpsService.reissueResetLink(anyLong()))
                .thenThrow(new TenantOpsException(
                        TenantOpsException.Code.TENANT_NOT_FOUND, "租户不存在：id=9999"));

        mockMvc.perform(post("/api/v1/ops/tenants/9999/password-reset-link"))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(404);
                    assertThat(r.getResponse().getContentAsString()).contains("TENANT_NOT_FOUND");
                });
    }

    @Test
    void reissueResetLink_noMainAdmin_returns422() throws Exception {
        when(tenantOpsService.reissueResetLink(anyLong()))
                .thenThrow(new TenantOpsException(
                        TenantOpsException.Code.NO_MAIN_ADMIN, "租户尚未指定主管理员"));

        mockMvc.perform(post("/api/v1/ops/tenants/9001/password-reset-link"))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(422);
                    assertThat(r.getResponse().getContentAsString()).contains("NO_MAIN_ADMIN");
                });
    }

    @Test
    void getTenantDetail_happyPath_returnsAllSections() throws Exception {
        TenantOpsService.TenantBasic basic = new TenantOpsService.TenantBasic(
                "9001", "acme", "Acme Corp", "T1234ABCD", "运营备注",
                "1001", "CSM 张三", 50, 10737418240L,
                Instant.parse("2027-01-01T00:00:00Z"), "Asia/Shanghai",
                "Acme 全称", "91110108MA001", "软件", "50-200人", "北京", List.of("VIP"),
                1, "pending_activation", Instant.parse("2026-05-25T10:00:00Z"),
                Instant.parse("2026-05-27T08:00:00Z"));
        TenantOpsService.MainAdmin admin = new TenantOpsService.MainAdmin(
                "8001", "alice", "Alice Smith", "13800000000", "alice@acme.com", 0);
        com.wensaas.core.api.auth.user.CapabilitiesService.PackageView base =
                new com.wensaas.core.api.auth.user.CapabilitiesService.PackageView(
                        "base", "基础包", null, false);
        TenantOpsService.SeatAddonHistoryItem hist = new TenantOpsService.SeatAddonHistoryItem(
                "1", 5, "首批加购", "1", "平台超级管理员", Instant.parse("2026-05-25T10:00:00Z"));
        TenantOpsService.SeatAddonSummary seats = new TenantOpsService.SeatAddonSummary(5, List.of(hist));
        TenantOpsService.TenantDetail detail = new TenantOpsService.TenantDetail(
                basic, admin, List.of(base), seats);
        when(tenantOpsService.getTenantDetail(9001L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/ops/tenants/9001"))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(200);
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"tenantNo\":\"T1234ABCD\"");
                    assertThat(body).contains("\"activationStatus\":\"pending_activation\"");
                    assertThat(body).contains("\"username\":\"alice\"");
                    assertThat(body).contains("\"mobile\":\"13800000000\"");
                    assertThat(body).contains("\"email\":\"alice@acme.com\"");
                    assertThat(body).contains("\"csmName\":\"CSM 张三\"");
                    assertThat(body).contains("\"memberCap\":50");
                    assertThat(body).contains("\"code\":\"base\"");
                    assertThat(body).contains("\"totalQuantity\":5");
                    assertThat(body).contains("\"quantity\":5");
                });
    }

    @Test
    void getTenantDetail_tenantNotFound_returns404() throws Exception {
        when(tenantOpsService.getTenantDetail(9999L))
                .thenThrow(new TenantOpsException(
                        TenantOpsException.Code.TENANT_NOT_FOUND, "租户不存在：id=9999"));

        mockMvc.perform(get("/api/v1/ops/tenants/9999"))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(404);
                    assertThat(r.getResponse().getContentAsString()).contains("TENANT_NOT_FOUND");
                });
    }

    @Test
    void impersonate_happyPath_returnsNonce() throws Exception {
        ImpersonateService.NonceIssued issued = new ImpersonateService.NonceIssued(
                "test-nonce-uuid", "acme", 300L);
        when(impersonateService.startImpersonation(eq(9001L), eq(200L))).thenReturn(issued);

        mockMvc.perform(post("/api/v1/ops/tenants/9001/impersonate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("asUserId", 200))))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(200);
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"redeemNonce\":\"test-nonce-uuid\"");
                    assertThat(body).contains("\"targetTenantCode\":\"acme\"");
                    assertThat(body).contains("\"expiresInSec\":300");
                });
    }

    @Test
    void impersonate_missingAsUserId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ops/tenants/9001/impersonate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asUserId\":null}"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(400));
    }

    @Test
    void impersonate_targetUserNotFound_returns404() throws Exception {
        when(impersonateService.startImpersonation(anyLong(), anyLong()))
                .thenThrow(new ImpersonateException(
                        ImpersonateException.Code.IMPERSONATE_TARGET_USER_NOT_FOUND, "目标用户不存在"));

        mockMvc.perform(post("/api/v1/ops/tenants/9001/impersonate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("asUserId", 999))))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(404);
                    assertThat(r.getResponse().getContentAsString())
                            .contains("IMPERSONATE_TARGET_USER_NOT_FOUND");
                });
    }

    @Test
    void impersonate_nestedNotAllowed_returns400() throws Exception {
        when(impersonateService.startImpersonation(anyLong(), anyLong()))
                .thenThrow(new ImpersonateException(
                        ImpersonateException.Code.IMPERSONATE_NESTED_NOT_ALLOWED, "代登录中不允许再次代登录"));

        mockMvc.perform(post("/api/v1/ops/tenants/9001/impersonate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("asUserId", 200))))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(400);
                    assertThat(r.getResponse().getContentAsString())
                            .contains("IMPERSONATE_NESTED_NOT_ALLOWED");
                });
    }
}
