package com.wensaas.core.web.member;

import com.wensaas.core.api.auth.permission.MemberAdminService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 record→Lombok fluent POJO 改造后，响应 DTO 在 <b>Jackson 3</b>（tools.jackson，与生产响应同源）下
 * 仍按字段名序列化（{@code @JsonAutoDetect(fieldVisibility=ANY)}），JSON 结构与原 record 一致。
 * 这是「改造前后响应 JSON diff 为空」约束的序列化机制守卫——若字段可见性失效会序列化成空对象。
 */
class MemberDtoSerializationTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void memberListItem_serializesAllFieldsByName() {
        MemberAdminService.MemberListItem it = new MemberAdminService.MemberListItem()
                .id(5L).username("zhangsan").realName("张三")
                .mobile("138****0001").email("z***n@example.com")
                .mainDeptId(10L).mainDeptName("研发中心")
                .secondaryDeptCount(1).status(1).owner(false)
                .roleIds(List.of(1L)).roleNames(List.of("管理员"))
                .positionCode("ENG").positionName("工程师")
                .loginLocked(false).leaderDeptCount(0);

        String body = json.writeValueAsString(it);

        assertThat(body)
                .contains("\"id\":5")
                .contains("\"username\":\"zhangsan\"")
                .contains("\"realName\":\"张三\"")
                .contains("\"mainDeptId\":10")
                .contains("\"mainDeptName\":\"研发中心\"")
                .contains("\"secondaryDeptCount\":1")
                .contains("\"roleIds\":[1]")
                .contains("\"roleNames\":[\"管理员\"]")
                .contains("\"owner\":false")
                .contains("\"loginLocked\":false")
                .contains("\"positionName\":\"工程师\"")
                .contains("\"leaderDeptCount\":0");
    }

    @Test
    void memberDetail_recordWrapper_serializesNestedPojoDeptBinding() {
        MemberAdminService.DeptBinding main = new MemberAdminService.DeptBinding(10L);
        main.deptName("研发中心");
        MemberAdminService.MemberDetail d = new MemberAdminService.MemberDetail(
                5L, "zhangsan", "张三", "138****0001", "z***n@example.com",
                null, null, null, "ENG", "工程师", 1, false, false, 0L,
                main, List.of(), List.of(1L), List.of(), null, null, null, List.of());

        String body = json.writeValueAsString(d);

        // record 外层 + 嵌套可变 POJO DeptBinding 都正常出键
        assertThat(body)
                .contains("\"mainDept\":{")
                .contains("\"deptId\":10")
                .contains("\"deptName\":\"研发中心\"");
    }

    @Test
    void deptBinding_unfilledName_serializesNull_notMissing() {
        MemberAdminService.DeptBinding b = new MemberAdminService.DeptBinding(7L);

        String body = json.writeValueAsString(b);

        assertThat(body).contains("\"deptId\":7").contains("\"deptName\":null");
    }
}
