package com.wensaas.core.impl.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 领域边界静态门禁。纯静态分析，不连库。
 *
 * <p>两类规则：
 * <ul>
 *   <li><b>硬约束（全绿）</b>：已收口的 {@code sys_user} / {@code sys_tenant} 边界 + sanctioned 白名单 +
 *       sys_user 写仅 {@code UserCommandService}——防回潮。</li>
 *   <li><b>全域总门禁（FreezingArchRule）</b>：「任何 domain 不依赖其它 domain 的 {@code *Mapper}」，
 *       以当前约 70 处跨域 Mapper 依赖为<b>冻结基线</b>、只拦<b>新增</b>违规（棘轮）。基线随代码提交于
 *       {@code archunit_freeze_store/}；清理一处后用 {@code freeze.refreeze=true} 重刷。
 *       sys_user/sys_tenant 由上面的专项规则精确管，本总门禁排除二者避免双重记账。</li>
 * </ul>
 */
class DomainBoundaryArchTest {

    private static final JavaClasses IMPL = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.wensaas.core.impl");

    private static final String IMPL_PREFIX = "com.wensaas.core.impl.";

    /** domain = {@code com.wensaas.core.impl.} 之后的首个包段；非 impl 类返回 {@code null}。 */
    private static String domainOf(JavaClass c) {
        String p = c.getPackageName();
        if (!p.startsWith(IMPL_PREFIX)) {
            return null;
        }
        String rest = p.substring(IMPL_PREFIX.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    /** {@code SysUserMapper}（auth 主数据）只允许成员管理共享域 {auth, permission, ops, tenant} 直引。 */
    @Test
    void sysUserMapper_onlyMemberManagingDomains() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages(
                        "..impl.auth..", "..impl.permission..", "..impl.ops..", "..impl.tenant..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.wensaas.core.impl.auth.user.SysUserMapper")
                .because("跨域读 sys_user 须经 core-api/auth UserQueryService，统一脱敏与缓存口径、保证读路径单一落点");
        rule.check(IMPL);
    }

    /** {@code SysTenantMapper}（租户主数据）只允许 {tenant, ops} 直引（owner=tenant + ops 管理/报表豁免）。 */
    @Test
    void sysTenantMapper_onlyTenantAndOps() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("..impl.tenant..", "..impl.ops..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.wensaas.core.impl.tenant.SysTenantMapper")
                .because("跨域读 sys_tenant 须经 core-api/tenant TenantQueryService，避免各域自读造成口径分裂");
        rule.check(IMPL);
    }

    /**
     * {@code SysUserMapper} 的<b>写方法</b>（insert/updateById/update/delete）只允许 {@code UserCommandService} 调用
     * （写收敛）。读方法不限（sanctioned 成员管理域仍直接读）。这把「单一写入口」从约定变为结构约束。
     */
    @Test
    void sysUserMapper_writeMethods_onlyUserCommandService() {
        DescribedPredicate<JavaCall<?>> userMapperWrite =
                new DescribedPredicate<>("调用 SysUserMapper 写方法") {
                    private final Set<String> writes = Set.of("insert", "updateById", "update", "delete");

                    @Override
                    public boolean test(JavaCall<?> call) {
                        return call.getTargetOwner().isAssignableTo("com.wensaas.core.impl.auth.user.SysUserMapper")
                                && writes.contains(call.getName());
                    }
                };
        ArchRule rule = noClasses()
                .that().doNotHaveFullyQualifiedName("com.wensaas.core.impl.auth.user.UserCommandService")
                .should().callMethodWhere(userMapperWrite)
                .because("sys_user 写一律经 auth/UserCommandService，保证写后失效有唯一落点");
        rule.check(IMPL);
    }

    /**
     * 全域总门禁：任何 domain 不得依赖其它 domain 的 {@code *Mapper}。以现状为冻结基线、只拦新增（棘轮）。
     * sys_user/sys_tenant 由上面的专项规则精确管，此处排除避免双重记账。
     */
    @Test
    void noCrossDomainMapperDependency_frozen() {
        ArchCondition<JavaClass> notDependOnForeignDomainMapper =
                new ArchCondition<>("不依赖其它 domain 的 *Mapper") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        String from = domainOf(item);
                        if (from == null) {
                            return;
                        }
                        for (Dependency dep : item.getDirectDependenciesFromSelf()) {
                            JavaClass target = dep.getTargetClass().getBaseComponentType();
                            String name = target.getSimpleName();
                            if (!name.endsWith("Mapper")
                                    || name.equals("SysUserMapper") || name.equals("SysTenantMapper")) {
                                continue;  // 非 Mapper，或由专项规则管的两个
                            }
                            String to = domainOf(target);
                            if (to == null || to.equals(from)) {
                                continue;  // 非 impl Mapper，或同域（合法）
                            }
                            events.add(SimpleConditionEvent.violated(item, dep.getDescription()));
                        }
                    }
                };
        ArchRule rule = FreezingArchRule.freeze(
                classes().that().resideInAPackage("..impl..")
                        .should(notDependOnForeignDomainMapper)
                        .as("Mapper 域私有（全域）——冻结现状、只拦新增跨域 Mapper 依赖"));
        rule.check(IMPL);
    }
}
