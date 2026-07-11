package com.wensaas.core.impl.tenant.selfservice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.tenant.selfservice.TenantCompanyException;
import com.wensaas.core.api.tenant.TenantContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.tenant.SysTenantRouteAlias;
import com.wensaas.core.impl.tenant.SysTenantRouteAliasMapper;

class TenantCompanyServiceImplTest {

    private static final long TENANT = 9100L;

    private SysTenantMapper tenantMapper;
    private FileService fileService;
    private SysTenantRouteAliasMapper aliasMapper;
    private TenantCompanyServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 纯 Mockito 测试无 SqlSession，手动初始化 MP 实体元数据，使 LambdaQueryWrapper/方法引用可解析。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysTenant.class);
        TableInfoHelper.initTableInfo(assistant, SysTenantRouteAlias.class);
    }

    @BeforeEach
    void setup() {
        tenantMapper = mock(SysTenantMapper.class);
        fileService = mock(FileService.class);
        aliasMapper = mock(SysTenantRouteAliasMapper.class);
        service = new TenantCompanyServiceImpl(tenantMapper, fileService, aliasMapper);
        TenantContext.set(TENANT);
        UserContext.set(new UserPrincipal(888L, TENANT, "admin", "sess", true, null, false, null));
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        UserContext.clear();
    }

    @Test
    void changeRouteSlot_registersAliasForOldCode() {
        SysTenant t = new SysTenant();
        t.setId(TENANT);
        t.setCode("old-slug");
        t.setCodeChangedAt(null);            // 立即可改
        when(tenantMapper.selectById(TENANT)).thenReturn(t);
        when(tenantMapper.selectCount(any())).thenReturn(0L);

        service.changeRouteSlot("new-slug");

        // 旧 code 登记为别名（90 天内重定向）
        ArgumentCaptor<SysTenantRouteAlias> cap = ArgumentCaptor.forClass(SysTenantRouteAlias.class);
        verify(aliasMapper).insert((SysTenantRouteAlias) cap.capture());
        SysTenantRouteAlias alias = cap.getValue();
        assertThat(alias.getOldCode()).isEqualTo("old-slug");
        assertThat(alias.getTenantId()).isEqualTo(TENANT);
        assertThat(alias.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(89));
        assertThat(alias.getExpiresAt()).isBefore(LocalDateTime.now().plusDays(91));
        // 先清理 old_code = 旧/新 code 的陈旧别名
        verify(aliasMapper).delete(any());
    }

    @Test
    void changeRouteSlot_unchanged_noAlias() {
        SysTenant t = new SysTenant();
        t.setId(TENANT);
        t.setCode("samecode");
        when(tenantMapper.selectById(TENANT)).thenReturn(t);

        service.changeRouteSlot("samecode");     // 幂等

        verify(aliasMapper, never()).insert((SysTenantRouteAlias) any());
    }

    @Test
    void changeRouteSlot_taken_throwsNoAlias() {
        SysTenant t = new SysTenant();
        t.setId(TENANT);
        t.setCode("old-slug");
        when(tenantMapper.selectById(TENANT)).thenReturn(t);
        when(tenantMapper.selectCount(any())).thenReturn(1L);   // 已被占用

        assertThatThrownBy(() -> service.changeRouteSlot("taken-slug"))
                .isInstanceOf(TenantCompanyException.class);
        verify(aliasMapper, never()).insert((SysTenantRouteAlias) any());
    }

    @Test
    void changeRouteSlot_invalidFormat_throws() {
        SysTenant t = new SysTenant();
        t.setId(TENANT);
        t.setCode("old-slug");
        when(tenantMapper.selectById(TENANT)).thenReturn(t);

        // 大写、下划线、以连字符结尾、过短（<5）均非合法子域名 label
        for (String bad : new String[]{"ABCDE", "a_b_c", "acme-", "abcd", "-abcd"}) {
            assertThatThrownBy(() -> service.changeRouteSlot(bad))
                    .isInstanceOf(TenantCompanyException.class)
                    .satisfies(ex -> assertThat(((TenantCompanyException) ex).code())
                            .isEqualTo(TenantCompanyException.Code.INVALID_ROUTE_SLOT));
        }
        verify(aliasMapper, never()).insert((SysTenantRouteAlias) any());
    }
}
