package com.wensaas.core.web.me;

import com.wensaas.core.api.auth.user.CapabilitiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class CapabilitiesControllerTest {

    private CapabilitiesService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        service = mock(CapabilitiesService.class);
        CapabilitiesController controller = new CapabilitiesController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void capabilities_returnsAllFields() throws Exception {
        CapabilitiesService.Capabilities cap = new CapabilitiesService.Capabilities(
                "1234567890",
                "9001",
                false,
                List.of("dict.group.list"),
                "DEPT_AND_BELOW",
                List.of("100", "110"),
                List.of(new CapabilitiesService.ModuleView("crm", "客户", "1.0.0", false)),
                List.of(),
                List.of(new CapabilitiesService.MenuNode("system/dictionaries", "字典", "Book", 30, List.of())),
                true,
                "opsAdmin",
                "张三",
                true);
        when(service.resolve()).thenReturn(cap);

        mockMvc.perform(get("/api/v1/me/capabilities"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"userId\":\"1234567890\"");
                    assertThat(body).contains("\"tenantId\":\"9001\"");
                    assertThat(body).contains("\"tenantOwner\":false");
                    assertThat(body).contains("\"dataScope\":\"DEPT_AND_BELOW\"");
                    assertThat(body).contains("\"permissions\":[\"dict.group.list\"]");
                    assertThat(body).contains("\"visibleDeptIds\":[\"100\",\"110\"]");
                    assertThat(body).contains("\"enabledModules\":");
                    assertThat(body).contains("\"activePackages\":[]");
                    assertThat(body).contains("\"menus\":");
                    assertThat(body).contains("\"impersonating\":true");
                    assertThat(body).contains("\"operatorName\":\"opsAdmin\"");
                    assertThat(body).contains("\"realName\":\"张三\"");
                    assertThat(body).contains("\"screenshotWatermark\":true");
                });
    }

    @Test
    void capabilities_emptyState_returnsEmptyCollections() throws Exception {
        CapabilitiesService.Capabilities cap = new CapabilitiesService.Capabilities(
                "0", "0", false,
                List.of(), "SELF", List.of(),
                List.of(), List.of(), List.of(),
                false, null,
                null, true);
        when(service.resolve()).thenReturn(cap);

        mockMvc.perform(get("/api/v1/me/capabilities"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"permissions\":[]");
                    assertThat(body).contains("\"enabledModules\":[]");
                    assertThat(body).contains("\"menus\":[]");
                });
    }
}
