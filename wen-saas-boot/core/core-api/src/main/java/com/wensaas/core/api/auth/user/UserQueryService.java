package com.wensaas.core.api.auth.user;

import java.time.LocalDateTime;

/**
 * 用户读服务：auth domain 对外暴露的「按 id 取单用户」查询。
 *
 * <p>跨 domain 读取 {@code sys_user} 一律走 auth 读服务，<b>不得直接注入 {@code SysUserMapper}</b>。
 * 本接口是 ISP 拆分后的「按 id 取单用户」面；另有按用途隔离的窄接口：成员计数 {@link MemberCountQuery}、
 * 运营账号查找 {@link OpsAccountQuery}、投递明文联系方式 {@link UserDeliveryContactQuery}、
 * 成员导出 {@link MemberExportQuery}——消费方按需注入对应窄接口，均由同一 Impl 实现。
 */
public interface UserQueryService {

    /**
     * 用户姓名 {@code realName}；用户不存在返回 {@code null}。
     *
     * <p>{@code real_name} 为 NOT NULL（建用户入口均保证非空），故直接取、不再回退 username。
     * 不含敏感字段。沿用当前租户范围语义（按 id 直查，受 MP TenantLine 注入约束）。
     */
    String realName(long userId);

    /**
     * 按 id 取用户档；不存在返回 {@code null}。<b>不含 {@code passwordHash}；mobile/email 为明文</b>
     * （读时由 {@code EncryptedStringTypeHandler} 透明解密）。
     *
     * <p><b>面向服务层内部</b>：脱敏 / 门控 / 审计是 web→前端的边界职责、<b>不在此</b>——服务间取基础信息直读即可。
     * 调用方对持有的明文负责：<b>不得把本档原样透出前端响应</b>（要展示走掩码的通用接口；要明文走权限门控的
     * {@code /contact} 端点）。受调用方租户上下文 / MP TenantLine 约束。
     */
    UserDetail userById(long userId);

    /**
     * 按手机号或邮箱定位用户；找不到返回 {@code null}。含 {@code @} 视为邮箱、否则视为手机号，
     * 主列 miss 时回落另一列。联系方式是确定性加密列，等值查询由实现内部完成密文比对——
     * 消费方（如业务导入按联系方式定位负责人）不需要也不应接触加密细节。
     * 明文语义与 {@link #userById} 同（调用方不得原样透出前端）。
     */
    UserDetail userByContact(String mobileOrEmail);

    /**
     * 用户档（{@link #userById}）。<b>无 {@code passwordHash}；{@code mobile}/{@code email} 为明文</b>，
     * 调用方负责脱敏（见 {@link #userById} javadoc）。
     */
    record UserDetail(Long id, Long tenantId, String username, String realName,
                      String mobile, String email, Integer gender, java.time.LocalDate birthday,
                      String avatarFileId, String empNo, String positionCode,
                      Integer status, LocalDateTime lastLoginAt, LocalDateTime createdAt) {}
}
