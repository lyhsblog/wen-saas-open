package com.wensaas.core.impl.auth.user;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;

import java.time.LocalDateTime;

/**
 * 用户写服务（auth domain）：{@code sys_user} 的<b>唯一写入口</b>，跨域写一律经此。
 *
 * <p><b>为什么收敛写</b>：{@code sys_user} 的跨域写都是「改一条记录的属性」（建号 / 改资料 / 改状态 /
 * 改密 / 盖登录时间），形态简单可收敛。集中到本服务后，<em>未来若给用户基础信息加缓存</em>，缓存失效只有
 * 这一处落点、调用方漏不掉——把「写后一致性」从「靠每个调用方记得」变成结构性保证。当前用户基础信息未缓存，
 * 故本服务暂只做持久化收敛 + 占好失效的位置。
 *
 * <p><b>读不在此</b>：成员列表 / 按用户名或加密手机号等值查 / 存在性 count 等是各成员管理域（auth /
 * permission / ops / tenant）自身上下文的查询，形态发散，仍由各域直接读 {@link SysUserMapper}
 * （sanctioned）。本服务只收口<b>写</b>。方法级 ArchUnit 禁止本类之外调用 Mapper 的写方法。
 *
 * <p>{@code SysUser} 实体作为「补丁载体」跨域传入：调用方建一个只含 id + 待改字段的 {@code SysUser}
 * 交给 {@link #updateById}；MP 跳过 null 字段。需要显式置空列或跨租户写的，用下面的具名场景方法。
 */
public class UserCommandService {

    private final SysUserMapper userMapper;

    public UserCommandService(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 新建用户，回填并返回主键 id。 */
    public long insert(SysUser user) {
        userMapper.insert(user);
        return user.getId();
    }

    /** 按 id 局部更新（MP 跳过 null 字段）：{@code patch} 须含 id + 待改字段。 */
    public void updateById(SysUser patch) {
        userMapper.updateById(patch);
    }

    /**
     * 场景：清空岗位 {@code position_code}。
     *
     * <p>MP {@code updateById} 跳过 null 字段、无法把列改回 null，故走显式 {@code set(col, null)}
     * （原 {@code MemberAdminServiceImpl} 内联写法收口至此）。
     */
    public void clearPosition(long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPositionCode, null));
    }

    /**
     * 场景：盖最近登录时间 {@code last_login_at = now}。
     *
     * <p>登录流程无环境租户上下文，本写<b>恒跨租户</b>——内部自管 MP TenantLine 旁路，调用方无需再包
     * {@code @CrossTenantQuery} / {@link InterceptorIgnoreHelper}（原 Mfa / Auth 各自的旁路样板收口至此）。
     */
    public void touchLastLogin(long userId) {
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        try {
            SysUser patch = new SysUser();
            patch.setLastLoginAt(LocalDateTime.now());
            userMapper.update(patch, new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, userId));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }
}
