package com.wensaas.core.impl.auth.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wensaas.core.impl.auth.login.PasswordLoginIdentityProvider;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys_user Mapper。注意：登录查询场景需要绕过 TenantLine 注入（登录前还没有 TenantContext），
 * 由 {@link PasswordLoginIdentityProvider} 用 {@code @CrossTenantQuery} 控制。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
