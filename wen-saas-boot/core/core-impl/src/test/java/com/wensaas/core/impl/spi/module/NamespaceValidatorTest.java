package com.wensaas.core.impl.spi.module;

import com.wensaas.core.api.spi.BusinessModuleConflictException;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.DashboardCardProvider;
import com.wensaas.core.api.spi.ModuleMetadata;
import com.wensaas.core.api.spi.def.DashboardCardData;
import com.wensaas.core.api.spi.def.DashboardCardDef;
import com.wensaas.core.api.spi.def.DashboardCardDisplay;
import com.wensaas.core.api.spi.def.DashboardCardSpan;
import com.wensaas.core.api.spi.def.DictGroupDef;
import com.wensaas.core.api.spi.def.H5PageDef;
import com.wensaas.core.api.spi.def.NotificationTemplateDef;
import com.wensaas.core.api.spi.def.PackageDef;
import com.wensaas.core.api.spi.def.PermissionDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class NamespaceValidatorTest {

    private final NamespaceValidator validator = new NamespaceValidator();

    @Test
    void emptyList_passes() {
        validator.validate(List.of());
    }

    @Test
    void distinctModules_pass() {
        var crm = module("crm",
                List.of(PermissionDef.of("crm.customer.list", "查询")));
        var approval = module("approval",
                List.of(PermissionDef.of("approval.task.list", "查询")));
        validator.validate(List.of(crm, approval));
    }

    @Test
    void duplicateModuleCode_throws() {
        var a = module("crm", List.of());
        var b = module("crm", List.of());
        assertThatThrownBy(() -> validator.validate(List.of(a, b)))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("模块编码冲突")
                .hasMessageContaining("crm");
    }

    @Test
    void duplicatePermissionInSameModule_throws() {
        var crm = module("crm", List.of(
                PermissionDef.of("crm.customer.list", "A"),
                PermissionDef.of("crm.customer.list", "B")));
        assertThatThrownBy(() -> validator.validate(List.of(crm)))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("crm.customer.list")
                .hasMessageContaining("[crm]");
    }

    @Test
    void duplicatesAcrossExtensionPoints_areAllReported() {
        var crmA = new TestModule("crm", "1.0.0")
                .withPermissions(PermissionDef.of("a.b.c", "X"))
                .withDicts(DictGroupDef.of("crm.src", "来源"))
                .withH5(new H5PageDef("crm/h5", "H5"))
                .withTemplates(NotificationTemplateDef.of("crm.welcome", "欢迎"))
                .withCards(card("crm.kpi", "KPI"));
        var crmB = new TestModule("crm", "1.0.0")
                .withPermissions(PermissionDef.of("a.b.c", "Y"))
                .withDicts(DictGroupDef.of("crm.src", "来源"))
                .withH5(new H5PageDef("crm/h5", "H5"))
                .withTemplates(NotificationTemplateDef.of("crm.welcome", "欢迎"))
                .withCards(card("crm.kpi", "KPI"));

        Throwable thrown = catchThrowable(() -> validator.validate(List.of(crmA, crmB)));

        assertThat(thrown)
                .isInstanceOf(BusinessModuleConflictException.class);
        assertThat(thrown.getMessage())
                .contains("模块编码冲突")
                .contains("权限点编码")
                .contains("字典编码")
                .contains("H5 路由")
                .contains("通知模板编码")
                .contains("工作台卡片编码");
    }

    @Test
    void keyOutsideOwnerNamespace_throws() {
        // crm 模块声明了属于 approval 命名空间的权限点（M12 越界）
        var crm = module("crm",
                List.of(PermissionDef.of("approval.task.list", "越界")));
        assertThatThrownBy(() -> validator.validate(List.of(crm)))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("命名空间越界")
                .hasMessageContaining("approval.task.list")
                .hasMessageContaining("[crm]");
    }

    @Test
    void codePrefixWithoutSeparator_isRejected() {
        // code 'crm' 不应让 'crmother.x'（无分隔符边界）蒙混过关
        var crm = module("crm",
                List.of(PermissionDef.of("crmother.x", "Y")));
        assertThatThrownBy(() -> validator.validate(List.of(crm)))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("命名空间越界");
    }

    @Test
    void duplicatePackageCode_throws() {
        var a = new TestModule("crm", "1.0.0")
                .withPackages(PackageDef.of("crm.starter", "A", null));
        var b = new TestModule("crm2", "1.0.0")
                .withPackages(new PackageDef("crm.starter", "B", null, List.of("crm"), null, true));
        // b 同时触发编码冲突与命名空间越界（crm.starter 不在 crm2 空间），都该在聚合消息里
        Throwable thrown = catchThrowable(() -> validator.validate(List.of(a, b)));
        assertThat(thrown).isInstanceOf(BusinessModuleConflictException.class);
        assertThat(thrown.getMessage())
                .contains("预置套餐编码冲突")
                .contains("命名空间越界");
    }

    @Test
    void packageCodeOutsideOwnerNamespace_throws() {
        var crm = new TestModule("crm", "1.0.0")
                .withPackages(PackageDef.of("approval.starter", "越界包", null));
        assertThatThrownBy(() -> validator.validate(List.of(crm)))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("预置套餐编码命名空间越界")
                .hasMessageContaining("approval.starter");
    }

    @Test
    void packageModuleRefMissing_throws() {
        var crm = new TestModule("crm", "1.0.0")
                .withPackages(new PackageDef("crm.suite", "套件", null,
                        List.of("oa", "hr"), null, true));
        var oa = new TestModule("oa", "1.0.0");
        // hr 未启用/不存在 → 阻断；oa 存在不报
        assertThatThrownBy(() -> validator.validate(List.of(crm, oa)))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("crm.suite")
                .hasMessageContaining("引用不存在或未启用的模块")
                .hasMessageContaining("hr");
    }

    @Test
    void packageCrossModuleRefToEnabledModule_passes() {
        var crm = new TestModule("crm", "1.0.0")
                .withPackages(new PackageDef("crm.suite", "套件", null,
                        List.of("oa"), null, true));
        var oa = new TestModule("oa", "1.0.0");
        validator.validate(List.of(crm, oa));
    }

    private BusinessModuleDescriptor module(String code, List<PermissionDef> permissions) {
        return new TestModule(code, "1.0.0")
                .withPermissions(permissions.toArray(PermissionDef[]::new));
    }

    private static DashboardCardDef card(String code, String name) {
        return new DashboardCardDef(code, name, DashboardCardDisplay.NUMBER, DashboardCardSpan.THIRD,
                null, List.of(), 0, true, NoopCardProvider.class);
    }

    private static class NoopCardProvider implements DashboardCardProvider {
        @Override public DashboardCardData render() { return null; }
    }

    private static class TestModule implements BusinessModuleDescriptor {
        private final String code;
        private final String version;
        private List<PermissionDef> permissions = List.of();
        private List<DictGroupDef> dicts = List.of();
        private List<H5PageDef> h5 = List.of();
        private List<NotificationTemplateDef> templates = List.of();
        private List<DashboardCardDef> cards = List.of();
        private List<PackageDef> packages = List.of();

        TestModule(String code, String version) {
            this.code = code;
            this.version = version;
        }

        TestModule withPermissions(PermissionDef... items) { this.permissions = List.of(items); return this; }
        TestModule withDicts(DictGroupDef... items) { this.dicts = List.of(items); return this; }
        TestModule withH5(H5PageDef... items) { this.h5 = List.of(items); return this; }
        TestModule withTemplates(NotificationTemplateDef... items) { this.templates = List.of(items); return this; }
        TestModule withCards(DashboardCardDef... items) { this.cards = List.of(items); return this; }
        TestModule withPackages(PackageDef... items) { this.packages = List.of(items); return this; }

        @Override public ModuleMetadata metadata() { return ModuleMetadata.of(code, code, version); }
        @Override public List<PermissionDef> permissions() { return permissions; }
        @Override public List<DictGroupDef> dictGroups() { return dicts; }
        @Override public List<H5PageDef> h5Pages() { return h5; }
        @Override public List<NotificationTemplateDef> notificationTemplates() { return templates; }
        @Override public List<DashboardCardDef> dashboardCards() { return cards; }
        @Override public List<PackageDef> packages() { return packages; }
    }
}
