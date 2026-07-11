package com.wensaas.core.impl.notification.delivery;

import com.wensaas.core.api.auth.org.DeptQueryService;
import com.wensaas.core.api.auth.permission.UserRoleQueryService;
import com.wensaas.core.api.auth.user.UserDeliveryContactQuery;
import com.wensaas.core.api.auth.user.UserDeliveryContactQuery.UserContact;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.send.RecipientSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 把 {@link RecipientSpec} 展开为具体的 {@link Recipient} 列表。
 *
 * <p>按 type 不同走不同 mapper 查询：
 * <ul>
 *   <li>MEMBER：直接读 sys_user</li>
 *   <li>ROLE：查 sys_user_role → 收 user_ids → 读 sys_user</li>
 *   <li>DEPT：查 sys_user_dept（可选含子部门 by sys_dept.ancestors LIKE）→ 收 user_ids → 读 sys_user</li>
 *   <li>DEPT_LEADER：暂未实现展开（负责人数据在 sys_dept_leader，本处未接入），返回空 + log.warn</li>
 * </ul>
 *
 * <p>所有查询走 MP TenantLineInnerInterceptor 自动注入 tenant_id；调用方需保证
 * {@code TenantContext.require()} 已设置当前租户。
 */
public class RecipientResolver {

    private static final Logger log = LoggerFactory.getLogger(RecipientResolver.class);

    private final UserDeliveryContactQuery deliveryContacts;
    private final UserRoleQueryService userRoleQuery;
    private final DeptQueryService deptQuery;

    public RecipientResolver(UserDeliveryContactQuery deliveryContacts,
                             UserRoleQueryService userRoleQuery, DeptQueryService deptQuery) {
        this.deliveryContacts = deliveryContacts;
        this.userRoleQuery = userRoleQuery;
        this.deptQuery = deptQuery;
    }

    /**
     * 展开收件人。
     *
     * @return 去重后的 Recipient 列表（按首次出现顺序）；空集合表示无可触达对象
     */
    public List<Recipient> resolve(List<RecipientSpec> specs) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (RecipientSpec spec : specs) {
            switch (spec.type()) {
                case MEMBER -> userIds.add(spec.value());
                case ROLE -> userIds.addAll(loadUserIdsByRole(spec.value()));
                case DEPT -> userIds.addAll(loadUserIdsByDept(spec.value(), spec.includeSubDepts()));
                case DEPT_LEADER -> {
                    // 暂未接入 sys_dept_leader 展开，先降级为空集
                    log.warn("[notify] RecipientType.DEPT_LEADER v1 未实现：deptId={}", spec.value());
                }
                default -> throw new IllegalStateException("未知 RecipientType: " + spec.type());
            }
        }
        if (userIds.isEmpty()) {
            return List.of();
        }
        return loadRecipients(userIds);
    }

    private List<Long> loadUserIdsByRole(long roleId) {
        return userRoleQuery.userIdsByRole(roleId);
    }

    private List<Long> loadUserIdsByDept(long deptId, boolean includeSub) {
        return deptQuery.userIdsByDept(deptId, includeSub);
    }

    private List<Recipient> loadRecipients(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        // 投递用途明文联系方式（启用 + 保序 + 过滤禁用，由 UserDeliveryContactQuery 收口）
        List<Recipient> out = new ArrayList<>();
        for (UserContact c : deliveryContacts.activeContactsForDelivery(new ArrayList<>(userIds))) {
            out.add(new Recipient(c.userId(), c.tenantId(), c.email(), c.mobile()));
        }
        return out;
    }
}
