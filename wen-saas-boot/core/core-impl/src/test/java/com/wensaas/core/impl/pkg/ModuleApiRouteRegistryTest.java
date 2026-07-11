package com.wensaas.core.impl.pkg;

import com.wensaas.core.api.spi.BusinessModuleConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleApiRouteRegistryTest {

    private ModuleApiRouteRegistry registry;

    @BeforeEach
    void setup() {
        registry = new ModuleApiRouteRegistry();
    }

    @Test
    void findModuleCode_exactPrefixMatch() {
        registry.register("/api/v1/crm/", "crm");
        assertThat(registry.findModuleCode("/api/v1/crm/customers")).contains("crm");
        assertThat(registry.findModuleCode("/api/v1/crm/customers/1")).contains("crm");
    }

    @Test
    void findModuleCode_unregisteredPath_returnsEmpty() {
        registry.register("/api/v1/crm/", "crm");
        assertThat(registry.findModuleCode("/api/v1/me/capabilities")).isEmpty();
        assertThat(registry.findModuleCode("/api/v1/base/roles")).isEmpty();
        assertThat(registry.findModuleCode("/api/auth/login")).isEmpty();
    }

    @Test
    void findModuleCode_longestPrefixWins() {
        registry.register("/api/v1/crm/", "crm");
        registry.register("/api/v1/crm/sub/", "crm-sub");
        assertThat(registry.findModuleCode("/api/v1/crm/sub/items")).contains("crm-sub");
        assertThat(registry.findModuleCode("/api/v1/crm/customers")).contains("crm");
    }

    @Test
    void findModuleCode_emptyRegistry_returnsEmpty() {
        assertThat(registry.findModuleCode("/api/v1/crm/customers")).isEmpty();
    }

    @Test
    void register_sameModuleSamePrefix_idempotent() {
        registry.register("/api/v1/crm/", "crm");
        registry.register("/api/v1/crm/", "crm");
        assertThat(registry.findModuleCode("/api/v1/crm/x")).contains("crm");
    }

    @Test
    void register_conflictingModules_throwsConflictException() {
        registry.register("/api/v1/crm/", "crm");
        assertThatThrownBy(() -> registry.register("/api/v1/crm/", "other"))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("crm")
                .hasMessageContaining("other");
    }

    @Test
    void findModuleCode_multiplePrefixesSameModule() {
        registry.register("/api/v1/crm/", "crm");
        registry.register("/api/h5/crm/", "crm");
        assertThat(registry.findModuleCode("/api/v1/crm/customers")).contains("crm");
        assertThat(registry.findModuleCode("/api/h5/crm/home")).contains("crm");
    }
}
