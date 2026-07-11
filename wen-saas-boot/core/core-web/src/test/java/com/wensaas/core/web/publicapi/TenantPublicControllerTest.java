package com.wensaas.core.web.publicapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.tenant.SysTenantRouteAlias;
import com.wensaas.core.impl.tenant.SysTenantRouteAliasMapper;
import com.wensaas.core.impl.tenant.TenantPublicQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class TenantPublicControllerTest {

    private SysTenantMapper mapper;
    private SysTenantRouteAliasMapper aliasMapper;
    private FileService fileService;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mapper = mock(SysTenantMapper.class);
        aliasMapper = mock(SysTenantRouteAliasMapper.class);
        fileService = mock(FileService.class);
        TenantPublicQueryService service = new TenantPublicQueryService(mapper, aliasMapper, fileService);
        TenantPublicController controller = new TenantPublicController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TenantPublicRestExceptionAdvice())
                .build();
    }

    @Test
    void byCode_active_returnsFiveFields() throws Exception {
        SysTenant t = newTenant(9001L, "acme", "Acme 公司", "https://cdn/acme.png", 1);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(t);

        mockMvc.perform(get("/api/v1/public/tenants/by-code/acme"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"id\":\"9001\"");
                    assertThat(body).contains("\"code\":\"acme\"");
                    assertThat(body).contains("\"canonicalCode\":\"acme\"");
                    assertThat(body).contains("\"name\":\"Acme 公司\"");
                    assertThat(body).contains("\"logoUrl\":\"https://cdn/acme.png\"");
                    assertThat(body).contains("\"status\":\"active\"");
                    // 不暴露 mainAdminUserId / description / 内部字段
                    assertThat(body).doesNotContain("mainAdminUserId");
                    assertThat(body).doesNotContain("description");
                });
    }

    @Test
    void byCode_withLogoFileId_signsFreshUrl() throws Exception {
        SysTenant t = newTenant(9005L, "logo-co", "Logo 公司", "https://stale/old.png", 1);
        t.setLogoFileId("file-123");
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(t);
        when(fileService.signedDownloadUrl(eq("file-123"), any(Duration.class)))
                .thenReturn("https://signed/fresh.png");

        mockMvc.perform(get("/api/v1/public/tenants/by-code/logo-co"))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    // 实时签发优先于历史 logo_url 列
                    assertThat(body).contains("\"logoUrl\":\"https://signed/fresh.png\"");
                    assertThat(body).doesNotContain("stale");
                });
    }

    @Test
    void byCode_oldRouteAlias_resolvesWithCanonicalCode() throws Exception {
        // 当前 code 未命中
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        // 旧路由位别名命中（未过期）→ 指向租户 9006
        SysTenantRouteAlias alias = new SysTenantRouteAlias();
        alias.setTenantId(9006L);
        alias.setOldCode("old-slug");
        alias.setExpiresAt(LocalDateTime.now().plusDays(30));
        when(aliasMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(alias);
        when(mapper.selectById(9006L)).thenReturn(newTenant(9006L, "new-slug", "改名公司", null, 1));

        mockMvc.perform(get("/api/v1/public/tenants/by-code/old-slug"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    // 返回当前规范 code，前端据 canonicalCode != 请求 code 重定向
                    assertThat(body).contains("\"code\":\"new-slug\"");
                    assertThat(body).contains("\"canonicalCode\":\"new-slug\"");
                });
    }

    @Test
    void byCode_notFound_returns404Problem() throws Exception {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        mockMvc.perform(get("/api/v1/public/tenants/by-code/unknown"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(404))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("TENANT_NOT_FOUND");
                    assertThat(r.getResponse().getContentType()).contains("application/problem+json");
                });
    }

    @Test
    void byCode_softDeleted_returns404() throws Exception {
        // @TableLogic 自动 WHERE deleted_at IS NULL；模拟 mapper 返 null 即可
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        mockMvc.perform(get("/api/v1/public/tenants/by-code/deleted-co"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(404));
    }

    @Test
    void byCode_pausedStatus_mapsCorrectly() throws Exception {
        SysTenant t = newTenant(9002L, "pause-co", "暂停公司", null, 0);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(t);

        mockMvc.perform(get("/api/v1/public/tenants/by-code/pause-co"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"status\":\"paused\"");
                    assertThat(body).contains("\"logoUrl\":null");
                });
    }

    @Test
    void byCode_expiredStatus_mapsCorrectly() throws Exception {
        SysTenant t = newTenant(9003L, "exp-co", "过期公司", null, 2);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(t);

        mockMvc.perform(get("/api/v1/public/tenants/by-code/exp-co"))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"status\":\"expired\"");
                });
    }

    private SysTenant newTenant(long id, String code, String name, String logoUrl, int status) {
        SysTenant t = new SysTenant();
        t.setId(id);
        t.setCode(code);
        t.setName(name);
        t.setLogoUrl(logoUrl);
        t.setStatus(status);
        t.setMainAdminUserId(999L);
        t.setDescription("internal");
        return t;
    }
}
