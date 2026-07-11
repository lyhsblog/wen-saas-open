package com.wensaas.core.impl.demoguard;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 演示守卫配置：按「HTTP 方法 + Ant 路径」黑名单拦截写操作，供对外演示环境锁住高危接口。
 *
 * <p>规则对所有请求方生效（不区分账号/权限），因此演示者即便通过角色管理自提权、
 * 或新建账号，也无法绕过。规则由部署配置维护，底座不内置。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.demo-guard")
@Getter
@Setter
public class DemoGuardProperties {

    /**
     * 总开关。默认 false：不装配任何 Bean，零开销。
     */
    private boolean enabled = false;

    /**
     * 拦截规则列表，每条格式 {@code "METHOD[,METHOD] antPattern"}，
     * 如 {@code "PUT,DELETE /api/v1/ops/team/roles/**"}。格式非法启动即失败。
     */
    private List<String> rules = new ArrayList<>();
}
