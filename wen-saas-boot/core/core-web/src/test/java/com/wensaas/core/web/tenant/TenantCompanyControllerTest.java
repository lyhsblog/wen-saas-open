package com.wensaas.core.web.tenant;

import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.dict.DictSource;
import com.wensaas.core.api.tenant.selfservice.TenantCompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class TenantCompanyControllerTest {

    private DictService dictService;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        TenantCompanyService service = mock(TenantCompanyService.class);
        dictService = mock(DictService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TenantCompanyController(service, dictService))
                .build();
    }

    @Test
    void options_returnsIndustryAndScaleFromDict() throws Exception {
        when(dictService.listItems("INDUSTRY")).thenReturn(List.of(
                item("互联网", "互联网"), item("制造业", "制造业")));
        when(dictService.listItems("COMPANY_SIZE")).thenReturn(List.of(
                item("1-49", "1-49 人")));

        mockMvc.perform(get("/api/v1/base/company-profile/options"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"industries\"").contains("\"scales\"");
                    assertThat(body).contains("\"code\":\"互联网\"").contains("\"name\":\"1-49 人\"");
                });
    }

    private static DictItem item(String code, String name) {
        return new DictItem(1L, code, name, null, 0, 1, null, DictSource.SYSTEM, null, null);
    }
}
