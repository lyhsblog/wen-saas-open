package com.wensaas.core.api.openapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiAuthenticationExceptionTest {

    @Test
    void carriesCodeAndMessage() {
        OpenApiAuthenticationException e = new OpenApiAuthenticationException(
                OpenApiAuthenticationException.Code.INVALID_API_KEY, "凭证不存在");
        assertThat(e.getCode()).isEqualTo(OpenApiAuthenticationException.Code.INVALID_API_KEY);
        assertThat(e.getMessage()).isEqualTo("凭证不存在");
    }

    @Test
    void carriesCause() {
        RuntimeException cause = new RuntimeException("redis down");
        OpenApiAuthenticationException e = new OpenApiAuthenticationException(
                OpenApiAuthenticationException.Code.INVALID_SIGNATURE, "签名错", cause);
        assertThat(e.getCause()).isSameAs(cause);
    }
}
