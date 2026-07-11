package com.wensaas.core.impl.notification.access;

import com.wensaas.core.api.notification.access.RelatedObjectAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 关联对象访问权限路由。
 *
 * <ul>
 *   <li>按 {@code relatedType} 找对应 {@link RelatedObjectAccessChecker} 调用一次</li>
 *   <li>未注册 checker / {@code relatedType} 或 {@code relatedId} 为空 → 默认放行
 *   （与 {@link com.wensaas.core.impl.file.FilePermissionEvaluator} 同模式，业务模块按需接入）</li>
 *   <li>同 {@code relatedType} 注册多个 checker → 启动期抛 {@link IllegalStateException}</li>
 * </ul>
 */
public class RelatedObjectAccessEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RelatedObjectAccessEvaluator.class);

    private final Map<String, RelatedObjectAccessChecker> checkerByType;

    public RelatedObjectAccessEvaluator(List<RelatedObjectAccessChecker> checkers) {
        Map<String, RelatedObjectAccessChecker> map = new HashMap<>();
        for (RelatedObjectAccessChecker c : checkers) {
            String key = c.relatedType();
            if (key == null || key.isBlank()) {
                throw new IllegalStateException(
                        "RelatedObjectAccessChecker.relatedType() 不能为空: " + c.getClass().getName());
            }
            RelatedObjectAccessChecker prior = map.put(key, c);
            if (prior != null) {
                throw new IllegalStateException(
                        "relatedType=" + key + " 同时注册了多个 RelatedObjectAccessChecker: "
                                + prior.getClass().getName() + " vs " + c.getClass().getName());
            }
        }
        this.checkerByType = Map.copyOf(map);
        log.info("[RelatedObjectAccessEvaluator] {} checker(s) registered: {}",
                checkerByType.size(), checkerByType.keySet());
    }

    /**
     * 当前用户是否可访问该关联对象。
     *
     * @param relatedType   关联类型；为空 → 放行
     * @param relatedId     关联对象 ID；为空 → 放行
     * @param currentUserId 当前用户 ID（可能为 null）
     */
    public boolean canAccess(String relatedType, String relatedId, Long currentUserId) {
        if (relatedType == null || relatedType.isBlank() || relatedId == null || relatedId.isBlank()) {
            return true;
        }
        RelatedObjectAccessChecker checker = checkerByType.get(relatedType);
        if (checker == null) {
            // 未注册 checker：默认放行（业务模块尚未接入）
            return true;
        }
        return checker.canAccess(relatedId, currentUserId);
    }
}
