package com.wensaas.core.web.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wensaas.core.api.auth.permission.MemberAdminService;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.dict.DictSource;
import com.wensaas.core.web.member.dto.MemberRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class MemberAdminControllerTest {

    private MemberAdminService service;
    private DictService dictService;
    private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() {
        service = mock(MemberAdminService.class);
        dictService = mock(DictService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberAdminController(service, dictService)).build();
    }

    @Test
    void positions_returnsPositionDictItems() throws Exception {
        when(dictService.listItems("POSITION")).thenReturn(List.of(
                new DictItem(1L, "ENG", "工程师", null, 0, 1, null, DictSource.SYSTEM, null, null),
                new DictItem(2L, "PM", "产品经理", null, 0, 1, null, DictSource.SYSTEM, null, null)));

        mockMvc.perform(get("/api/v1/base/members/positions"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"code\":\"ENG\"").contains("\"name\":\"产品经理\"");
                });
    }

    @Test
    void create_threadsPositionCodeToCommand() throws Exception {
        when(service.createMember(any())).thenReturn(
                new MemberAdminService.CreatedMember(1L, "zhangsan", "tok", null));
        String body = json.writeValueAsString(new MemberRequests.CreateRequest(
                "zhangsan", "张三", "13800138000", null, 10L, List.of(), List.of(), "ENG", null));

        mockMvc.perform(post("/api/v1/base/members")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));

        ArgumentCaptor<MemberAdminService.CreateMemberCommand> cap =
                ArgumentCaptor.forClass(MemberAdminService.CreateMemberCommand.class);
        verify(service).createMember(cap.capture());
        assertThat(cap.getValue().positionCode()).isEqualTo("ENG");
    }
}
