package com.wensaas.core.web.customfield;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomFieldEqualsArgumentResolverTest {

    private final CustomFieldEqualsArgumentResolver resolver = new CustomFieldEqualsArgumentResolver();

    @SuppressWarnings("unused")
    void endpoint(@CustomFieldEquals Map<String, Object> cfEquals, Map<String, Object> plain) {
    }

    private MethodParameter param(int index) throws NoSuchMethodException {
        return new MethodParameter(
                getClass().getDeclaredMethod("endpoint", Map.class, Map.class), index);
    }

    @Test
    void supports_onlyAnnotatedMapParameter() throws Exception {
        assertThat(resolver.supportsParameter(param(0))).isTrue();
        assertThat(resolver.supportsParameter(param(1))).isFalse();
    }

    @Test
    void resolve_collectsCfPrefixedNonBlankParams() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("status", "LEAD");
        request.addParameter("cf_level", "VIP");
        request.addParameter("cf_blank", "  ");
        request.addParameter("cf_region", "east");

        Object resolved = resolver.resolveArgument(param(0), null, new ServletWebRequest(request), null);

        assertThat(resolved).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) resolved;
        assertThat(map).containsExactlyInAnyOrderEntriesOf(
                Map.of("cf_level", "VIP", "cf_region", "east"));
    }

    @Test
    void resolve_noHit_returnsEmptyMapNotNull() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("status", "LEAD");

        Object resolved = resolver.resolveArgument(param(0), null, new ServletWebRequest(request), null);

        assertThat(resolved).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) resolved).isEmpty();
    }
}
