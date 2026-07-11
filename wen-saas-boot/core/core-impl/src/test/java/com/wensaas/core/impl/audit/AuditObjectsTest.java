package com.wensaas.core.impl.audit;

import com.wensaas.core.api.audit.AuditObjectRef;
import com.wensaas.core.api.audit.AuditObjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ② 多主对象发射缓冲 {@link AuditObjects}。 */
class AuditObjectsTest {

    @AfterEach
    void tearDown() {
        AuditObjects.clear();
    }

    @Test
    void drain_returnsAddedRefsInOrder_andClears() {
        AuditObjects.add("角色", "1", "管理员");
        AuditObjects.add("角色", "2", "财务");

        List<AuditObjectRef> drained = AuditObjects.drain();

        assertThat(drained).containsExactly(
                new AuditObjectRef("角色", "1", "管理员"),
                new AuditObjectRef("角色", "2", "财务"));
        // drain 后已清空
        assertThat(AuditObjects.drain()).isEmpty();
    }

    @Test
    void add_nullId_ignored() {
        AuditObjects.add("角色", null, "x");
        assertThat(AuditObjects.drain()).isEmpty();
    }

    @Test
    void clear_emptiesBuffer() {
        AuditObjects.add("租户", "9", "测试");
        AuditObjects.clear();
        assertThat(AuditObjects.drain()).isEmpty();
    }

    @Test
    void drain_whenNothingAdded_returnsEmpty() {
        assertThat(AuditObjects.drain()).isEmpty();
    }
}
