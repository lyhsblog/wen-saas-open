package com.wensaas.core.impl.auth.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.user.MemberCountQuery;
import com.wensaas.core.api.auth.user.MemberExportQuery;
import com.wensaas.core.api.auth.user.OpsAccountQuery;
import com.wensaas.core.api.auth.user.UserDeliveryContactQuery;
import com.wensaas.core.api.auth.user.UserQueryService;
import com.wensaas.core.api.tenant.TenantContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * auth domain 用户读的<b>唯一实现</b>，一类实现 ISP 拆出的 5 个读接口：
 * 单用户 {@link UserQueryService} + 成员计数 {@link MemberCountQuery} + 运营账号 {@link OpsAccountQuery}
 * + 投递明文 {@link UserDeliveryContactQuery} + 成员导出 {@link MemberExportQuery}。本类是 auth 内唯一持
 * {@link SysUserMapper} 的对外读入口，跨 domain 消费方注入对应窄接口、而非裸查 Mapper。
 */
public class UserQueryServiceImpl
        implements UserQueryService, MemberCountQuery, OpsAccountQuery, UserDeliveryContactQuery, MemberExportQuery {

    private final SysUserMapper userMapper;
    private final com.wensaas.core.impl.security.SensitiveFieldCipher cipher;

    public UserQueryServiceImpl(SysUserMapper userMapper,
                                com.wensaas.core.impl.security.SensitiveFieldCipher cipher) {
        this.userMapper = userMapper;
        this.cipher = cipher;
    }

    @Override
    public String realName(long userId) {
        SysUser u = userMapper.selectById(userId);
        return u == null ? null : u.getRealName();
    }

    @Override
    public UserDetail userById(long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            return null;
        }
        // mobile/email 经 EncryptedStringTypeHandler 读时已解密为明文；不带 passwordHash。
        return toDetail(u);
    }

    @Override
    public UserDetail userByContact(String mobileOrEmail) {
        if (mobileOrEmail == null || mobileOrEmail.isBlank()) {
            return null;
        }
        String v = mobileOrEmail.trim();
        // 确定性加密列：等值查询对密文比对（同明文恒同密文）；含 @ 先按邮箱，miss 回落另一列
        boolean emailFirst = v.contains("@");
        SysUser u = selectByEncrypted(emailFirst ? "email" : "mobile", v);
        if (u == null) {
            u = selectByEncrypted(emailFirst ? "mobile" : "email", v);
        }
        return u == null ? null : toDetail(u);
    }

    private SysUser selectByEncrypted(String column, String plaintext) {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<>();
        q.apply(column + " = {0}", cipher.encrypt(plaintext));
        q.last("LIMIT 1");
        return userMapper.selectOne(q);
    }

    private static UserDetail toDetail(SysUser u) {
        return new UserDetail(u.getId(), u.getTenantId(), u.getUsername(), u.getRealName(),
                u.getMobile(), u.getEmail(), u.getGender(), u.getBirthday(),
                u.getAvatarFileId(), u.getEmpNo(), u.getPositionCode(),
                u.getStatus(), u.getLastLoginAt(), u.getCreatedAt());
    }

    @Override
    public List<Long> activeOpsUserIds() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getId)
                        .eq(SysUser::getTenantId, TenantContext.PLATFORM_TENANT_ID)
                        .eq(SysUser::getStatus, 1)).stream()
                .map(SysUser::getId).toList();
    }

    @Override
    public List<OpsUserBrief> activeOpsUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, TenantContext.PLATFORM_TENANT_ID)
                        .eq(SysUser::getStatus, 1)).stream()
                .map(u -> new OpsUserBrief(u.getId(), u.getRealName()))
                .toList();
    }

    @Override
    public long countActiveMembers(long tenantId) {
        return userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getStatus, 1));
    }

    @Override
    public long countMembers(long tenantId) {
        return userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId));
    }

    @Override
    public UserContact contactForDelivery(long userId) {
        SysUser u = userMapper.selectById(userId);
        return u == null ? null : new UserContact(u.getId(), u.getTenantId(), u.getEmail(), u.getMobile());
    }

    @Override
    public List<MemberExportRecord> exportableMembers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .orderByAsc(SysUser::getId)).stream()
                .map(u -> new MemberExportRecord(u.getId(), u.getUsername(), u.getRealName(),
                        u.getMobile(), u.getEmail(), u.getEmpNo(), u.getStatus(), u.getCreatedAt()))
                .toList();
    }

    @Override
    public List<UserContact> activeContactsForDelivery(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, UserContact> byId = new LinkedHashMap<>();
        userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds)
                        .eq(SysUser::getStatus, 1)
                        .select(SysUser::getId, SysUser::getTenantId, SysUser::getEmail, SysUser::getMobile))
                .forEach(u -> byId.put(u.getId(),
                        new UserContact(u.getId(), u.getTenantId(), u.getEmail(), u.getMobile())));
        // 保持入参顺序，过滤禁用 / 不存在
        List<UserContact> out = new ArrayList<>(byId.size());
        for (Long uid : userIds) {
            UserContact c = byId.get(uid);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }
}
