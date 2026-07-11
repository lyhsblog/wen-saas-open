package com.wensaas.core.api.auth.user;

import java.util.Objects;
import com.wensaas.core.api.auth.permission.RequirePermission;

/**
 * 当前请求的认证主体。
 *
 * <p>由 AuthenticationFilter 从 JWT + Redis Session 还原后注入 {@link UserContext}；
 * 也可由 {@code AppKeyAuthenticationFilter}（开放 API 场景）注入。
 *
 * @param userId      用户 ID；开放 API 凭证调用时填凭证关联的 creator_id（无操作人时填 0）
 * @param tenantId    租户 ID；{@code 0} 表示运营账号
 * @param username    登录账号；开放 API 凭证调用时填 {@code "api-key:" + apiKey} 形式
 * @param sessionId   会话标识（JWT subject）；开放 API 凭证调用时填 {@code "api-key:" + apiKeyId} 形式
 * @param tenantOwner 是否为该租户的主管理员（{@code sys_tenant.main_admin_user_id == userId}）。
 *                    主管理员在权限检查 / 数据权限拦截器中跳过所有规则；
 *                    开放 API 凭证调用 {@code tenantOwner} 恒为 false
 * @param mainDeptId  操作人主部门 ID（{@code sys_user_dept.is_main = 1}）。可能为 null：
 *                    未归属任何部门、或运营账号 / 开放 API 凭证调用；
 *                    操作日志的 {@code operator_dept_id} 字段直取该值快照。
 * @param byApiKey    是否为开放 API 凭证（AppKey）登入。true 时本次请求由 {@code AppKeyAuthenticationFilter}
 *                    构造，{@code apiKeyId} 必非 null；功能权限按凭证创建者身份走与普通用户一致的
 *                    {@code @RequirePermission} 检查，数据权限拦截器对其不做行级过滤
 *                    （凭证定位是租户级系统对接，取租户内全量数据）。
 * @param apiKeyId    凭证 ID（{@code sys_api_key.id}）。仅 {@code byApiKey=true} 时非 null。
 * @param realName    操作人真实姓名（{@code sys_user.real_name}）快照，于登录创建会话时填充、随 Session 还原；
 *                    供审计操作人列 / 防截屏水印溯源**读内存**取用，免去每次写入 / 拉 capabilities 查库。
 *                    可能为 null：开放 API 凭证、导出等合成主体、或姓名为空——消费侧回退账号 / 按需查库。
 */
public record UserPrincipal(long userId, long tenantId, String username, String sessionId,
                            boolean tenantOwner, Long mainDeptId,
                            boolean byApiKey, Long apiKeyId, String realName) {

    public UserPrincipal {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(sessionId, "sessionId");
        if (byApiKey && apiKeyId == null) {
            throw new IllegalArgumentException("byApiKey=true 时 apiKeyId 必须非空");
        }
    }

    /** 兼容旧 8 参构造（不携带 realName 的合成主体 / 历史调用点）：realName 置 null。 */
    public UserPrincipal(long userId, long tenantId, String username, String sessionId,
                         boolean tenantOwner, Long mainDeptId, boolean byApiKey, Long apiKeyId) {
        this(userId, tenantId, username, sessionId, tenantOwner, mainDeptId, byApiKey, apiKeyId, null);
    }

    /** 是否为运营账号（tenant_id = 0）。 */
    public boolean isPlatform() {
        return tenantId == 0L;
    }
}
