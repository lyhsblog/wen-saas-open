package com.wensaas.core.api.request;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestContextTest {

    @AfterEach
    void cleanup() {
        RequestContext.clear();
    }

    @Test
    void setAndGet() {
        RequestContext.set("req-1");
        assertThat(RequestContext.get()).isEqualTo("req-1");
    }

    @Test
    void require_missing_throws() {
        assertThatThrownBy(RequestContext::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RequestContext 未设置");
    }

    @Test
    void clear_removesValue() {
        RequestContext.set("req-2");
        RequestContext.clear();
        assertThat(RequestContext.get()).isNull();
    }

    @Test
    void runAs_restoresPrevious() {
        RequestContext.set("outer");
        RequestContext.runAs("inner", () -> assertThat(RequestContext.get()).isEqualTo("inner"));
        assertThat(RequestContext.get()).isEqualTo("outer");
    }

    @Test
    void runAs_noPrevious_clearsAfter() {
        RequestContext.runAs("only", () -> assertThat(RequestContext.get()).isEqualTo("only"));
        assertThat(RequestContext.get()).isNull();
    }

    @Test
    void callAs_returnsValueAndRestores() {
        RequestContext.set("outer");
        String got = RequestContext.callAs("inner", RequestContext::get);
        assertThat(got).isEqualTo("inner");
        assertThat(RequestContext.get()).isEqualTo("outer");
    }

    @Test
    void restoreFromMessage_setsWhenNonBlank() {
        RequestContext.restoreFromMessage("msg-id");
        assertThat(RequestContext.get()).isEqualTo("msg-id");
    }

    @Test
    void restoreFromMessage_nullOrBlank_isNoop() {
        RequestContext.set("existing");
        RequestContext.restoreFromMessage(null);
        RequestContext.restoreFromMessage("  ");
        assertThat(RequestContext.get()).isEqualTo("existing");
    }
}
