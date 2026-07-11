package com.wensaas.core.impl.permission.eval;

import com.wensaas.core.api.auth.permission.PermissionDeniedException;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequirePermissionAspectTest {

    private static final UserPrincipal MEMBER = new UserPrincipal(7L, 1L, "u", "s", false, null, false, null);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ===== granted 判定（直接走 resolver.has / hasAny）=====

    @Test
    void granted_singleValueHit() {
        PermissionResolver resolver = mock(PermissionResolver.class);
        when(resolver.has(1L, 7L, "crm.customer.view")).thenReturn(true);
        RequirePermissionAspect aspect = new RequirePermissionAspect(resolver);
        assertThat(aspect.granted(AnnHelper.value("crm.customer.view"), MEMBER)).isTrue();
    }

    @Test
    void granted_anyOfHit() {
        PermissionResolver resolver = mock(PermissionResolver.class);
        when(resolver.hasAny(anyLong(), anyLong(), any())).thenReturn(true);
        RequirePermissionAspect aspect = new RequirePermissionAspect(resolver);
        assertThat(aspect.granted(AnnHelper.anyOf("a", "b", "c"), MEMBER)).isTrue();
    }

    @Test
    void granted_noneHit_false() {
        PermissionResolver resolver = mock(PermissionResolver.class);
        // resolver.has 未 stub → 默认 false
        RequirePermissionAspect aspect = new RequirePermissionAspect(resolver);
        assertThat(aspect.granted(AnnHelper.value("crm.customer.edit"), MEMBER)).isFalse();
    }

    // ===== around 流程 =====

    @Test
    void around_userContextMissing_throwsNotAuthenticated() throws Throwable {
        PermissionResolver resolver = mock(PermissionResolver.class);
        RequirePermissionAspect aspect = new RequirePermissionAspect(resolver);

        ProceedingJoinPoint pjp = mockJoinPoint(SecuredEndpoints.class.getMethod("singleValue"), new SecuredEndpoints());

        assertThatThrownBy(() -> aspect.around(pjp))
                .isInstanceOf(PermissionDeniedException.class)
                .satisfies(e -> assertThat(((PermissionDeniedException) e).getCode())
                        .isEqualTo(PermissionDeniedException.Code.NOT_AUTHENTICATED));
        verify(resolver, never()).has(anyLong(), anyLong(), any());
    }

    @Test
    void around_tenantOwner_skipsResolverAndProceeds() throws Throwable {
        PermissionResolver resolver = mock(PermissionResolver.class);
        RequirePermissionAspect aspect = new RequirePermissionAspect(resolver);

        UserContext.set(new UserPrincipal(99L, 1L, "owner", "sess-x", true, null, false, null));

        ProceedingJoinPoint pjp = mockJoinPoint(SecuredEndpoints.class.getMethod("singleValue"), new SecuredEndpoints());
        when(pjp.proceed()).thenReturn("ok");

        assertThat(aspect.around(pjp)).isEqualTo("ok");
        verify(resolver, never()).has(anyLong(), anyLong(), any());
    }

    @Test
    void around_hasPermission_proceeds() throws Throwable {
        PermissionResolver resolver = mock(PermissionResolver.class);
        when(resolver.has(1L, 7L, "crm.customer.view")).thenReturn(true);

        RequirePermissionAspect aspect = new RequirePermissionAspect(resolver);
        UserContext.set(MEMBER);

        ProceedingJoinPoint pjp = mockJoinPoint(SecuredEndpoints.class.getMethod("singleValue"), new SecuredEndpoints());
        when(pjp.proceed()).thenReturn("ok");

        assertThat(aspect.around(pjp)).isEqualTo("ok");
    }

    @Test
    void around_lacksPermission_throwsForbidden() throws Throwable {
        PermissionResolver resolver = mock(PermissionResolver.class);
        // resolver.has 未 stub → 默认 false

        RequirePermissionAspect aspect = new RequirePermissionAspect(resolver);
        UserContext.set(MEMBER);

        ProceedingJoinPoint pjp = mockJoinPoint(SecuredEndpoints.class.getMethod("singleValue"), new SecuredEndpoints());

        assertThatThrownBy(() -> aspect.around(pjp))
                .isInstanceOf(PermissionDeniedException.class)
                .satisfies(e -> assertThat(((PermissionDeniedException) e).getCode())
                        .isEqualTo(PermissionDeniedException.Code.FORBIDDEN));
    }

    private static ProceedingJoinPoint mockJoinPoint(java.lang.reflect.Method method, Object target) {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);
        when((Signature) pjp.getSignature()).thenReturn(sig);
        when(pjp.getTarget()).thenReturn(target);
        return pjp;
    }

    // -- fixtures --------------------------------------------------------

    static class SecuredEndpoints {
        @RequirePermission("crm.customer.view")
        public String singleValue() { return "ok"; }
    }

    static class AnnHelper {
        static RequirePermission value(String v) {
            return new RequirePermissionStub(v, new String[0]);
        }
        static RequirePermission anyOf(String... v) {
            return new RequirePermissionStub("", v);
        }
    }

    record RequirePermissionStub(String value, String[] anyOf) implements RequirePermission {
        @Override public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return RequirePermission.class;
        }
    }
}
