package com.wensaas.core.impl.permission.integration;

import com.wensaas.core.api.auth.org.DeptAdminException;
import com.wensaas.core.api.auth.org.DeptAdminService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.permission.eval.PermissionCacheEvictor;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeptAdminServiceIntegrationTest {

    private static final long TENANT_ID = 9701L;
    private static final long OTHER_TENANT = 9702L;

    @Autowired JdbcTemplate jdbc;
    @Autowired DeptAdminService service;
    @Autowired PermissionCacheEvictor evictor;

    @BeforeAll
    void setupClass() {
        cleanup();
    }

    @AfterAll
    void teardownClass() {
        cleanup();
        TenantContext.clear();
    }

    @BeforeEach
    void setTenant() {
        cleanup();
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        evictor.evictAll();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_user_dept WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT);
        jdbc.update("DELETE FROM sys_dept WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT);
    }

    @Test
    void createTree_andListTree() {
        DeptAdminService.DeptView root = service.create(new DeptAdminService.CreateDeptRequest(0L, "研发中心", 0));
        DeptAdminService.DeptView be = service.create(new DeptAdminService.CreateDeptRequest(root.id(), "后端组", 0));
        DeptAdminService.DeptView fe = service.create(new DeptAdminService.CreateDeptRequest(root.id(), "前端组", 1));

        List<DeptAdminService.DeptView> tree = service.listTree();
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).id()).isEqualTo(root.id());
        assertThat(tree.get(0).children()).extracting(DeptAdminService.DeptView::name)
                .containsExactly("后端组", "前端组");
    }

    @Test
    void duplicateName_sameParent_throws() {
        DeptAdminService.DeptView root = service.create(new DeptAdminService.CreateDeptRequest(0L, "A1", 0));
        service.create(new DeptAdminService.CreateDeptRequest(root.id(), "组1", 0));
        assertThatThrownBy(() -> service.create(new DeptAdminService.CreateDeptRequest(root.id(), "组1", 1)))
                .isInstanceOf(DeptAdminException.class)
                .extracting(e -> ((DeptAdminException) e).code())
                .isEqualTo(DeptAdminException.Code.DEPT_NAME_DUPLICATE);
    }

    @Test
    void deleteWithChildren_throws() {
        DeptAdminService.DeptView root = service.create(new DeptAdminService.CreateDeptRequest(0L, "B1", 0));
        service.create(new DeptAdminService.CreateDeptRequest(root.id(), "B1.1", 0));
        assertThatThrownBy(() -> service.delete(root.id()))
                .isInstanceOf(DeptAdminException.class)
                .extracting(e -> ((DeptAdminException) e).code())
                .isEqualTo(DeptAdminException.Code.DEPT_HAS_CHILDREN);
    }

    @Test
    void moveDept_rewritesAncestors_andRejectsCycle() {
        DeptAdminService.DeptView root = service.create(new DeptAdminService.CreateDeptRequest(0L, "C1", 0));
        DeptAdminService.DeptView child = service.create(new DeptAdminService.CreateDeptRequest(root.id(), "C1.1", 0));
        DeptAdminService.DeptView grand = service.create(new DeptAdminService.CreateDeptRequest(child.id(), "C1.1.1", 0));

        // move grand 到 root 下；ancestors 由 /root/child/ → /root/
        DeptAdminService.DeptView moved = service.move(grand.id(), root.id());
        String newAncestors = jdbc.queryForObject(
                "SELECT ancestors FROM sys_dept WHERE id = ?", String.class, grand.id());
        assertThat(newAncestors).isEqualTo("/" + root.id() + "/");

        // 防循环：尝试把 root 移到 child 下
        assertThatThrownBy(() -> service.move(root.id(), child.id()))
                .isInstanceOf(DeptAdminException.class)
                .extracting(e -> ((DeptAdminException) e).code())
                .isEqualTo(DeptAdminException.Code.DEPT_PARENT_IS_DESCENDANT);
    }

    @Test
    void moveDept_subtreeAncestorsRewritten() {
        DeptAdminService.DeptView a = service.create(new DeptAdminService.CreateDeptRequest(0L, "D1", 0));
        DeptAdminService.DeptView b = service.create(new DeptAdminService.CreateDeptRequest(a.id(), "D1.1", 0));
        DeptAdminService.DeptView c = service.create(new DeptAdminService.CreateDeptRequest(b.id(), "D1.1.1", 0));

        // move b 到 root 下；c 的 ancestors 需重写：原 /a/b/ → /b/
        service.move(b.id(), 0L);
        String cAncestors = jdbc.queryForObject(
                "SELECT ancestors FROM sys_dept WHERE id = ?", String.class, c.id());
        assertThat(cAncestors).isEqualTo("/" + b.id() + "/");
    }

    @Test
    void crossTenant_cannotAccess() {
        long otherId = TenantContext.callAs(OTHER_TENANT, () ->
                service.create(new DeptAdminService.CreateDeptRequest(0L, "其他租户根", 0)).id());
        TenantContext.set(TENANT_ID);
        assertThatThrownBy(() -> service.getDetail(otherId))
                .isInstanceOf(DeptAdminException.class)
                .extracting(e -> ((DeptAdminException) e).code())
                .isEqualTo(DeptAdminException.Code.DEPT_NOT_FOUND);
    }
}
