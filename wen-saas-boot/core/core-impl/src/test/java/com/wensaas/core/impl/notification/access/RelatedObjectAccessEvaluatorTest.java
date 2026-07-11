package com.wensaas.core.impl.notification.access;

import com.wensaas.core.api.notification.access.RelatedObjectAccessChecker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelatedObjectAccessEvaluatorTest {

    private static RelatedObjectAccessChecker checker(String type, boolean allow) {
        return new RelatedObjectAccessChecker() {
            @Override public String relatedType() { return type; }
            @Override public boolean canAccess(String relatedId, Long currentUserId) { return allow; }
        };
    }

    @Test
    void noChecker_defaultsAllow() {
        var eval = new RelatedObjectAccessEvaluator(List.of());
        assertThat(eval.canAccess("Customer", "123", 7L)).isTrue();
    }

    @Test
    void nullOrBlankTypeOrId_allow() {
        var eval = new RelatedObjectAccessEvaluator(List.of(checker("Customer", false)));
        assertThat(eval.canAccess(null, "123", 7L)).isTrue();
        assertThat(eval.canAccess("Customer", null, 7L)).isTrue();
        assertThat(eval.canAccess(" ", "123", 7L)).isTrue();
        assertThat(eval.canAccess("Customer", " ", 7L)).isTrue();
    }

    @Test
    void registeredChecker_delegated() {
        var eval = new RelatedObjectAccessEvaluator(List.of(checker("Customer", false), checker("Contract", true)));
        assertThat(eval.canAccess("Customer", "1", 7L)).isFalse();
        assertThat(eval.canAccess("Contract", "1", 7L)).isTrue();
        // 未注册类型 → 放行
        assertThat(eval.canAccess("Unknown", "1", 7L)).isTrue();
    }

    @Test
    void blankRelatedType_throwsOnConstruction() {
        assertThatThrownBy(() -> new RelatedObjectAccessEvaluator(List.of(checker(" ", true))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("relatedType");
    }

    @Test
    void duplicateRelatedType_throwsOnConstruction() {
        assertThatThrownBy(() -> new RelatedObjectAccessEvaluator(
                List.of(checker("Customer", true), checker("Customer", false))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Customer");
    }
}
