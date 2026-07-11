package com.wensaas.core.impl.openapi.gateway;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IpWhitelistMatcherTest {

    private final IpWhitelistMatcher m = new IpWhitelistMatcher();

    @Test
    void emptyWhitelistAllowsAny() {
        assertThat(m.matches("1.2.3.4", null)).isTrue();
        assertThat(m.matches("1.2.3.4", List.of())).isTrue();
    }

    @Test
    void blankClientIpRejectedWhenWhitelistPresent() {
        assertThat(m.matches(null, List.of("1.2.3.4"))).isFalse();
        assertThat(m.matches("  ", List.of("1.2.3.4"))).isFalse();
    }

    @Test
    void exactIpMatches() {
        assertThat(m.matches("10.1.2.3", List.of("10.1.2.3"))).isTrue();
        assertThat(m.matches("10.1.2.4", List.of("10.1.2.3"))).isFalse();
    }

    @Test
    void cidrMatches() {
        assertThat(m.matches("10.1.2.3", List.of("10.0.0.0/8"))).isTrue();
        assertThat(m.matches("11.1.2.3", List.of("10.0.0.0/8"))).isFalse();
        assertThat(m.matches("192.168.1.50", List.of("192.168.1.0/24"))).isTrue();
        assertThat(m.matches("192.168.2.50", List.of("192.168.1.0/24"))).isFalse();
    }

    @Test
    void cidrSlashZeroMatchesAll() {
        assertThat(m.matches("1.2.3.4", List.of("0.0.0.0/0"))).isTrue();
        assertThat(m.matches("255.255.255.255", List.of("0.0.0.0/0"))).isTrue();
    }

    @Test
    void mixedWhitelistAtLeastOneMatches() {
        assertThat(m.matches("203.0.113.5", List.of("10.0.0.0/8", "203.0.113.5", "1.2.3.4"))).isTrue();
        assertThat(m.matches("5.5.5.5", List.of("10.0.0.0/8", "203.0.113.5"))).isFalse();
    }

    @Test
    void malformedRuleIgnoredNotThrowing() {
        assertThat(m.matches("1.2.3.4", List.of("not-an-ip", "1.2.3.4"))).isTrue();
        assertThat(m.matches("1.2.3.4", List.of("10.0.0.0/99"))).isFalse();
    }
}
