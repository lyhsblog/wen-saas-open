package com.wensaas.core.impl.permission.org;

import com.wensaas.core.api.auth.org.DeptQueryService.DeptInfo;
import com.wensaas.core.impl.permission.org.DeptService.DeptNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code deptInfosOfTenant}：整租户部门缓存瘦投影 → {@link DeptInfo} 的映射单测，
 * 确认走 {@link DeptService} 缓存、不触达 Mapper。
 */
class DeptQueryServiceImplTest {

    @Test
    void deptInfosOfTenant_由缓存瘦投影映射_不触达Mapper() {
        SysUserDeptMapper userDeptMapper = mock(SysUserDeptMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        DeptService deptService = mock(DeptService.class);
        when(deptService.deptMap(9300L)).thenReturn(Map.of(
                10L, new DeptNode(10L, 0L, "总公司", "/0/", 1, 1),
                20L, new DeptNode(20L, 10L, "华东区", "/0/10/", 2, 0)));

        var infos = new DeptQueryServiceImpl(userDeptMapper, deptMapper, deptService)
                .deptInfosOfTenant(9300L);

        assertThat(infos).containsOnlyKeys(10L, 20L);
        assertThat(infos.get(10L)).isEqualTo(new DeptInfo(10L, "总公司", "/0/"));
        // 停用部门（status=0）不过滤，照常进入快照
        assertThat(infos.get(20L)).isEqualTo(new DeptInfo(20L, "华东区", "/0/10/"));
        verifyNoInteractions(userDeptMapper, deptMapper);
    }
}
