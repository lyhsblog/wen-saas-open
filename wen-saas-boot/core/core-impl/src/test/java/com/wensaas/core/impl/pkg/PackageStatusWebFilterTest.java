package com.wensaas.core.impl.pkg;

import com.wensaas.core.api.pkg.PackageStatusException;
import com.wensaas.core.api.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackageStatusWebFilterTest {

    private static final long TENANT_ID = 200L;
    private static final String MODULE = "crm";

    private PackageStatusGuard guard;
    private ModuleApiRouteRegistry routeRegistry;
    private PackageStatusWebFilter filter;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setup() throws Exception {
        guard = mock(PackageStatusGuard.class);
        routeRegistry = mock(ModuleApiRouteRegistry.class);
        filter = new PackageStatusWebFilter(guard, routeRegistry);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void nullTenantContext_skipsGuard_proceedsChain() throws Exception {
        // TenantContext not set → null
        when(request.getRequestURI()).thenReturn("/api/v1/crm/customers");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(guard, never()).requireAccess(null, MODULE, true);
    }

    @Test
    void platformTenant_skipsGuard_proceedsChain() throws Exception {
        TenantContext.set(TenantContext.PLATFORM_TENANT_ID);
        when(request.getRequestURI()).thenReturn("/api/v1/crm/customers");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void platformReservedPath_noModuleCode_proceedsChain() throws Exception {
        TenantContext.set(TENANT_ID);
        when(request.getRequestURI()).thenReturn("/api/v1/me/capabilities");
        when(request.getMethod()).thenReturn("GET");
        when(routeRegistry.findModuleCode("/api/v1/me/capabilities")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void businessModuleRead_guardPasses_proceedsChain() throws Exception {
        TenantContext.set(TENANT_ID);
        when(request.getRequestURI()).thenReturn("/api/v1/crm/customers");
        when(request.getMethod()).thenReturn("GET");
        when(routeRegistry.findModuleCode("/api/v1/crm/customers")).thenReturn(Optional.of(MODULE));

        filter.doFilterInternal(request, response, chain);

        verify(guard).requireAccess(TENANT_ID, MODULE, false);
        verify(chain).doFilter(request, response);
    }

    @Test
    void businessModuleWrite_guardPasses_proceedsChain() throws Exception {
        TenantContext.set(TENANT_ID);
        when(request.getRequestURI()).thenReturn("/api/v1/crm/customers");
        when(request.getMethod()).thenReturn("POST");
        when(routeRegistry.findModuleCode("/api/v1/crm/customers")).thenReturn(Optional.of(MODULE));

        filter.doFilterInternal(request, response, chain);

        verify(guard).requireAccess(TENANT_ID, MODULE, true);
        verify(chain).doFilter(request, response);
    }

    @Test
    void packageReadonly_writeBlocked_returns403() throws Exception {
        TenantContext.set(TENANT_ID);
        when(request.getRequestURI()).thenReturn("/api/v1/crm/customers");
        when(request.getMethod()).thenReturn("POST");
        when(routeRegistry.findModuleCode("/api/v1/crm/customers")).thenReturn(Optional.of(MODULE));
        org.mockito.Mockito.doThrow(new PackageStatusException(
                PackageStatusException.Code.PACKAGE_READONLY, MODULE, "套餐只读"))
                .when(guard).requireAccess(TENANT_ID, MODULE, true);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(403);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void packageUnauthorized_readBlocked_returns402() throws Exception {
        TenantContext.set(TENANT_ID);
        when(request.getRequestURI()).thenReturn("/api/v1/crm/customers");
        when(request.getMethod()).thenReturn("GET");
        when(routeRegistry.findModuleCode("/api/v1/crm/customers")).thenReturn(Optional.of(MODULE));
        org.mockito.Mockito.doThrow(new PackageStatusException(
                PackageStatusException.Code.PACKAGE_UNAUTHORIZED, MODULE, "未购买"))
                .when(guard).requireAccess(TENANT_ID, MODULE, false);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(402);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void patchMethod_recognizedAsWrite() throws Exception {
        TenantContext.set(TENANT_ID);
        when(request.getRequestURI()).thenReturn("/api/v1/crm/customers/1");
        when(request.getMethod()).thenReturn("PATCH");
        when(routeRegistry.findModuleCode("/api/v1/crm/customers/1")).thenReturn(Optional.of(MODULE));

        filter.doFilterInternal(request, response, chain);

        verify(guard).requireAccess(TENANT_ID, MODULE, true);
    }
}
