package com.wensaas.core.web.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 spec 装配。
 *
 * <p>{@code wen-saas-boot.openapi-doc.enabled=true}（默认）时启用 {@code /v3/api-docs} 端点。
 * 前端 AI 工具链直接消费该 JSON 生成 TS 客户端，不依赖人类交互 UI（已选 starter-webmvc-api 无 UI 版）。
 *
 * <p>分组（GroupedOpenApi）按路径前缀切：
 * <ul>
 *   <li>{@code auth} — {@code /api/auth/**}</li>
 *   <li>{@code base} — {@code /api/v1/base/**}（底座租户域能力，免套餐拦截）</li>
 *   <li>{@code biz} — {@code /api/v1/biz/**}（业务模块，按套餐拦截）</li>
 *   <li>{@code ops} — {@code /api/v1/ops/**}（运营域）</li>
 *   <li>{@code openapi} — {@code /api/v1/openapi/**}（AppKey 鉴权）</li>
 *   <li>{@code files} — {@code /api/v1/files/**}（混合鉴权：JWT 业务 + HMAC Local 端点）</li>
 *   <li>{@code public} — {@code /api/v1/public/**}（无鉴权）</li>
 *   <li>{@code me} — {@code /api/v1/me/**}（当前用户视角）</li>
 *   <li>{@code all} — 全量（默认 spec 端点）</li>
 * </ul>
 *
 * <p>两套 securityScheme：
 * <ul>
 *   <li>{@code bearer-jwt} — Authorization: Bearer &lt;token&gt;（默认走 /api/auth/login 拿）</li>
 *   <li>{@code app-key} — X-Wen-API-Key + X-Wen-Signature + X-Wen-Timestamp（开放 API）</li>
 * </ul>
 * 端点默认走 bearer-jwt；{@code /api/v1/openapi/**} 在 Controller 上标 {@code @SecurityRequirement(name="app-key")} 覆盖。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "wen-saas-boot.openapi-doc", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class OpenApiDocConfiguration {

    private static final String SCHEME_BEARER_JWT = "bearer-jwt";
    private static final String SCHEME_APP_KEY = "app-key";

    @Bean
    public OpenAPI wenSaasBootOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wen SaaS Boot REST API")
                        .description("""
                                多租户 SaaS 管理后台底座对外 HTTP 契约。

                                - 鉴权方式：JWT Bearer（默认）/ AppKey HMAC（开放 API 子集）
                                - 错误响应统一 RFC 7807 ProblemDetail（application/problem+json）
                                - ID 字段统一 string 类型（Long 雪花 ID 超 JS safe integer）
                                - 时间字段 ISO-8601 带 +08:00 offset
                                """)
                        .version("v1")
                        .contact(new Contact().name("Wen SaaS Boot").url("https://github.com/wensaas/wen-saas-boot"))
                        .license(new License().name("Internal").url("about:blank")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("local dev"),
                        new Server().url("https://api.wensaas.example.com").description("test / prod")))
                .components(new Components()
                        .addSecuritySchemes(SCHEME_BEARER_JWT, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录返回的 JWT；Authorization: Bearer <token>。" +
                                        "Token 仅承载 sessionId，会话状态在 Redis"))
                        .addSecuritySchemes(SCHEME_APP_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Wen-API-Key")
                                .description("开放 API 凭证。当 require_signature=true 时需同时带 " +
                                        "X-Wen-Signature + X-Wen-Timestamp（HMAC-SHA256）")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_BEARER_JWT));
    }

    @Bean
    public GroupedOpenApi authGroup() {
        return GroupedOpenApi.builder().group("auth").pathsToMatch("/api/auth/**").build();
    }

    @Bean
    public GroupedOpenApi baseGroup() {
        return GroupedOpenApi.builder().group("base").pathsToMatch("/api/v1/base/**").build();
    }

    @Bean
    public GroupedOpenApi bizGroup() {
        return GroupedOpenApi.builder().group("biz").pathsToMatch("/api/v1/biz/**").build();
    }

    @Bean
    public GroupedOpenApi opsGroup() {
        return GroupedOpenApi.builder().group("ops").pathsToMatch("/api/v1/ops/**").build();
    }

    @Bean
    public GroupedOpenApi openApiGroup() {
        return GroupedOpenApi.builder().group("openapi").pathsToMatch("/api/v1/openapi/**").build();
    }

    @Bean
    public GroupedOpenApi filesGroup() {
        return GroupedOpenApi.builder().group("files").pathsToMatch("/api/v1/files/**").build();
    }

    @Bean
    public GroupedOpenApi publicGroup() {
        return GroupedOpenApi.builder().group("public").pathsToMatch("/api/v1/public/**").build();
    }

    @Bean
    public GroupedOpenApi meGroup() {
        return GroupedOpenApi.builder().group("me").pathsToMatch("/api/v1/me/**").build();
    }

    @Bean
    public GroupedOpenApi fileCenterGroup() {
        return GroupedOpenApi.builder().group("file-center").pathsToMatch("/api/v1/file-center/**").build();
    }

    @Bean
    public GroupedOpenApi messageGroup() {
        return GroupedOpenApi.builder().group("message").pathsToMatch("/api/v1/messages/**").build();
    }

    @Bean
    public GroupedOpenApi tasksGroup() {
        return GroupedOpenApi.builder().group("tasks").pathsToMatch("/api/v1/tasks/**").build();
    }

    @Bean
    public GroupedOpenApi importsGroup() {
        return GroupedOpenApi.builder().group("imports").pathsToMatch("/api/v1/imports/**").build();
    }
}
