package com.wensaas.crmdemo.exports;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.customfield.CustomFieldService;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.exports.spi.ExportSink;
import com.wensaas.crmdemo.repository.CrmDemoCustomerMapper;
import com.wensaas.crmdemo.service.CrmDemoCustomerService;
import com.wensaas.crmdemo.spi.CrmDemoModule;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 「我的客户」导出取数：与全量导出（{@link CrmDemoCustomerExportProvider}）同列结构、同取数管线，
 * 差异只有两点，与 {@code GET /mine} 端点的语义严格对齐——
 * <ul>
 *   <li>权限锚定个人视图 {@code crm_demo.customer.view}；</li>
 *   <li>负责人恒为申请人本人：服务端从 {@code UserContext} 取，<b>忽略前端传入的 ownerId</b>，
 *       行集即行级最窄档（SELF），不依赖申请人 data_scope 收窄。</li>
 * </ul>
 */
@Component
public class CrmDemoCustomerMineExportProvider extends CrmDemoCustomerExportProvider {

    public CrmDemoCustomerMineExportProvider(CrmDemoCustomerService customerService,
                                             CrmDemoCustomerMapper customerMapper,
                                             CustomFieldService customFieldService,
                                             DictService dictService) {
        super(customerService, customerMapper, customFieldService, dictService);
    }

    @Override
    public String objectCode() {
        return CrmDemoModule.EXPORT_CUSTOMER_MINE;
    }

    @Override
    public String requiredPermission() {
        return "crm_demo.customer.view";
    }

    @Override
    public long estimateCount(Map<String, String> filters) {
        return super.estimateCount(withSelfOwner(filters));
    }

    @Override
    public void export(Map<String, String> filters, ExportSink sink) {
        super.export(withSelfOwner(filters), sink);
    }

    private static Map<String, String> withSelfOwner(Map<String, String> filters) {
        Map<String, String> out = new HashMap<>(filters == null ? Map.of() : filters);
        out.put("ownerId", String.valueOf(UserContext.require().userId()));
        return out;
    }
}
