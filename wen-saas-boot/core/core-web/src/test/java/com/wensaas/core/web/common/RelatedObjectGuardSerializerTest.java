package com.wensaas.core.web.common;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.notification.access.RelatedObjectAccessChecker;
import com.wensaas.core.api.notification.access.RelatedObjectBody;
import com.wensaas.core.api.notification.access.RelatedObjectGuarded;
import com.wensaas.core.api.notification.access.RelatedObjectId;
import com.wensaas.core.api.notification.access.RelatedObjectLink;
import com.wensaas.core.api.notification.access.RelatedObjectType;
import com.wensaas.core.impl.notification.access.RelatedObjectAccessEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RelatedObjectGuardSerializerTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @RelatedObjectGuarded
    static class MsgView {
        public Long id;
        public String name;
        @RelatedObjectType public String relatedType;
        @RelatedObjectId public String relatedId;
        @RelatedObjectBody public String body;
        @RelatedObjectLink public String link;

        MsgView() {
        }

        MsgView(Long id, String name, String relatedType, String relatedId, String body, String link) {
            this.id = id;
            this.name = name;
            this.relatedType = relatedType;
            this.relatedId = relatedId;
            this.body = body;
            this.link = link;
        }
    }

    static class PlainView {
        public Long id;
        public String body;

        PlainView(Long id, String body) {
            this.id = id;
            this.body = body;
        }
    }

    private JsonMapper mapperWith(RelatedObjectAccessEvaluator evaluator) {
        SimpleModule module = new SimpleModule("test");
        module.setSerializerModifier(new RelatedObjectSerializerModifier(evaluator));
        return JsonMapper.builder().addModule(module).build();
    }

    private static RelatedObjectAccessChecker checker(String type, boolean allow, AtomicReference<Long> uidSink) {
        return new RelatedObjectAccessChecker() {
            @Override public String relatedType() { return type; }
            @Override public boolean canAccess(String relatedId, Long currentUserId) {
                uidSink.set(currentUserId);
                return allow;
            }
        };
    }

    @Test
    void granted_bodyAndLinkIntact() {
        var eval = new RelatedObjectAccessEvaluator(List.of(checker("Customer", true, new AtomicReference<>())));
        var json = mapperWith(eval).writeValueAsString(
                new MsgView(1L, "客户A", "Customer", "100", "正文内容", "/crm/customer/100"));
        assertThat(json).contains("正文内容").contains("/crm/customer/100").contains("客户A");
    }

    @Test
    void denied_bodyMasked_linkNull_idNameKept() {
        var eval = new RelatedObjectAccessEvaluator(List.of(checker("Customer", false, new AtomicReference<>())));
        var json = mapperWith(eval).writeValueAsString(
                new MsgView(1L, "客户A", "Customer", "100", "正文内容", "/crm/customer/100"));
        assertThat(json).contains("该消息关联的内容已不可见");
        assertThat(json).doesNotContain("正文内容");
        assertThat(json).doesNotContain("/crm/customer/100");
        assertThat(json).contains("客户A");          // name 摘要保留
        // link 置 null（Jackson 3.x 默认省略 null 字段，故 link 键整体消失）
        assertThat(json).doesNotContain("\"link\"");
    }

    @Test
    void noUserContext_passesNullUid() {
        var sink = new AtomicReference<Long>();
        var eval = new RelatedObjectAccessEvaluator(List.of(checker("Customer", true, sink)));
        mapperWith(eval).writeValueAsString(new MsgView(1L, "客户A", "Customer", "100", "正文", "/x"));
        assertThat(sink.get()).isNull();
    }

    @Test
    void userContextPresent_passesUid() {
        UserContext.set(new UserPrincipal(42L, 1L, "u", "s", false, null, false, null));
        var sink = new AtomicReference<Long>();
        var eval = new RelatedObjectAccessEvaluator(List.of(checker("Customer", true, sink)));
        mapperWith(eval).writeValueAsString(new MsgView(1L, "客户A", "Customer", "100", "正文", "/x"));
        assertThat(sink.get()).isEqualTo(42L);
    }

    @Test
    void unguardedDto_serializedUnchanged() {
        var eval = new RelatedObjectAccessEvaluator(List.of(checker("Customer", false, new AtomicReference<>())));
        var json = mapperWith(eval).writeValueAsString(new PlainView(9L, "明文正文"));
        assertThat(json).contains("明文正文");   // 未标注 → 不走探针
    }

    @Test
    void deniedButCopyPreservesOriginal() {
        var eval = new RelatedObjectAccessEvaluator(List.of(checker("Customer", false, new AtomicReference<>())));
        var original = new MsgView(1L, "客户A", "Customer", "100", "正文内容", "/crm/customer/100");
        mapperWith(eval).writeValueAsString(original);
        // 原对象不被污染（降级走副本）
        assertThat(original.body).isEqualTo("正文内容");
        assertThat(original.link).isEqualTo("/crm/customer/100");
    }
}
