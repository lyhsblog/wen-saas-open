package com.wensaas.core.impl.auth.oauth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.auth.oauth.OAuthBindConflictException;
import com.wensaas.core.api.auth.oauth.OAuthBindingService;
import com.wensaas.core.api.auth.oauth.OAuthClient;
import com.wensaas.core.api.auth.oauth.OAuthExchangeException;
import com.wensaas.core.api.auth.oauth.OAuthUserInfo;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户成员自助第三方账号绑定实现。已登录上下文调用，依赖 MP TenantLine 自动隔离（无需手工绕过）。
 */
public class OAuthBindingServiceImpl implements OAuthBindingService {

    private final Map<String, OAuthClient> clients;
    private final SysUserOAuthBindingMapper bindingMapper;

    public OAuthBindingServiceImpl(List<OAuthClient> clients, SysUserOAuthBindingMapper bindingMapper) {
        this.clients = new HashMap<>();
        for (OAuthClient c : clients) {
            this.clients.put(c.provider(), c);
        }
        this.bindingMapper = bindingMapper;
    }

    @Override
    public List<Binding> listByUser(long tenantId, long userId) {
        List<SysUserOAuthBinding> rows = bindingMapper.selectList(
                new LambdaQueryWrapper<SysUserOAuthBinding>()
                        .eq(SysUserOAuthBinding::getUserId, userId)
                        .isNull(SysUserOAuthBinding::getDeletedAt)
                        .orderByDesc(SysUserOAuthBinding::getBoundAt));
        return rows.stream()
                .map(r -> new Binding(r.getProvider(), r.getNickname(), r.getAvatarUrl(), r.getBoundAt()))
                .toList();
    }

    @Override
    public void bind(long tenantId, long userId, String provider, String authCode) {
        OAuthClient client = clients.get(provider);
        if (client == null) {
            throw new OAuthExchangeException("暂不支持的第三方登录渠道：" + provider);
        }
        OAuthUserInfo info = client.exchange(authCode);

        SysUserOAuthBinding b = new SysUserOAuthBinding();
        b.setTenantId(tenantId);
        b.setUserId(userId);
        b.setProvider(provider);
        b.setExternalId(info.externalId());
        b.setUnionId(info.unionId());
        b.setNickname(info.nickname());
        b.setAvatarUrl(info.avatarUrl());
        LocalDateTime now = LocalDateTime.now();
        b.setBoundAt(now);
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        b.setCreatedBy(userId);
        b.setUpdatedBy(userId);
        try {
            bindingMapper.insert(b);
        } catch (DuplicateKeyException e) {
            throw new OAuthBindConflictException("该第三方账号已被绑定，或当前账号已绑定该渠道", e);
        }
    }

    @Override
    public void unbind(long tenantId, long userId, String provider) {
        bindingMapper.update(null, Wrappers.<SysUserOAuthBinding>lambdaUpdate()
                .eq(SysUserOAuthBinding::getUserId, userId)
                .eq(SysUserOAuthBinding::getProvider, provider)
                .isNull(SysUserOAuthBinding::getDeletedAt)
                .set(SysUserOAuthBinding::getDeletedAt, LocalDateTime.now())
                .set(SysUserOAuthBinding::getUpdatedAt, LocalDateTime.now())
                .set(SysUserOAuthBinding::getUpdatedBy, userId));
    }
}
