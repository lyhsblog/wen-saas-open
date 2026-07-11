package com.wensaas.core.api.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.notification.send.RecipientType;

class RecipientSpecTest {

    @Test
    void factories_setExpectedFields() {
        RecipientSpec m = RecipientSpec.member(101L);
        assertThat(m.type()).isEqualTo(RecipientType.MEMBER);
        assertThat(m.value()).isEqualTo(101L);
        assertThat(m.includeSubDepts()).isFalse();

        RecipientSpec d = RecipientSpec.dept(20L, true);
        assertThat(d.type()).isEqualTo(RecipientType.DEPT);
        assertThat(d.includeSubDepts()).isTrue();

        RecipientSpec r = RecipientSpec.role(9L);
        assertThat(r.type()).isEqualTo(RecipientType.ROLE);

        RecipientSpec leader = RecipientSpec.deptLeader(5L);
        assertThat(leader.type()).isEqualTo(RecipientType.DEPT_LEADER);
    }
}
