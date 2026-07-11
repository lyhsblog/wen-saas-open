package com.wensaas.crmdemo.imports;

import com.wensaas.core.api.auth.user.UserQueryService;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.imports.ImportErrorCode;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.imports.NormalizedRow;
import com.wensaas.core.api.imports.ValidationSink;
import com.wensaas.core.api.spi.def.ImportFieldDef;
import com.wensaas.crmdemo.entity.CustomerStatus;
import com.wensaas.crmdemo.service.CrmDemoCustomerService;
import com.wensaas.crmdemo.spi.CrmDemoModule;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户导入预校验：为引擎的声明驱动 DB 校验提供取数口（客户编号 DB 查重 + 「客户来源」字典项匹配），
 * 声明覆盖不了的规则留在 {@link #validate}——阶段接受枚举名或中文 label 双写法；
 * 负责人按手机号/邮箱定位成员（经 {@code UserQueryService.userByContact}，加密等值由 auth 域内部完成），
 * 须存在且未停用。运行在请求线程（预览阶段），租户上下文已就绪。
 */
@Component
public class CrmDemoCustomerImportValidationResolver implements ImportValidationResolver {

    private final DictService dictService;
    private final UserQueryService userQuery;
    private final CrmDemoCustomerService customerService;

    public CrmDemoCustomerImportValidationResolver(DictService dictService, UserQueryService userQuery,
                                                   CrmDemoCustomerService customerService) {
        this.dictService = dictService;
        this.userQuery = userQuery;
        this.customerService = customerService;
    }

    @Override
    public String entityCode() {
        return CrmDemoModule.IMPORT_CUSTOMER;
    }

    @Override
    public Set<String> existingDbValues(String fieldKey, Set<String> values) {
        // 非查重字段返回空集（而非 null=未实现），避免引擎误判取数口缺失
        return "code".equals(fieldKey) ? customerService.existingCodes(values) : Set.of();
    }

    @Override
    public Map<String, Long> resolveReferences(ImportFieldDef field, Set<String> values) {
        if (!"source".equals(field.key())) {
            return Map.of();
        }
        // 「客户来源」字典项 code 或名称双写法均算命中；value=项 id
        Map<String, Long> tokenToId = new HashMap<>();
        for (DictItem item : dictService.listItems(CrmDemoModule.DICT_CUSTOMER_SOURCE)) {
            if (item.code() != null) {
                tokenToId.putIfAbsent(item.code(), item.id());
            }
            if (item.name() != null) {
                tokenToId.putIfAbsent(item.name(), item.id());
            }
        }
        Map<String, Long> out = new HashMap<>();
        for (String v : values) {
            if (tokenToId.containsKey(v)) {
                out.put(v, tokenToId.get(v));
            }
        }
        return out;
    }

    @Override
    public void validate(List<NormalizedRow> rows, ValidationSink sink) {
        for (NormalizedRow row : rows) {
            String status = row.get("status");
            if (!status.isBlank() && CustomerStatus.parseFlexible(status) == null) {
                sink.addError(row.rowNo(), "status", ImportErrorCode.REFERENCE_NOT_FOUND,
                        "阶段「" + status + "」不存在（可填 潜在/跟进中/成交/流失 或 LEAD/FOLLOWING/WON/LOST）");
            }
            String owner = row.get("owner");
            if (!owner.isBlank()) {
                var user = userQuery.userByContact(owner);
                if (user == null) {
                    sink.addError(row.rowNo(), "owner", ImportErrorCode.REFERENCE_NOT_FOUND,
                            "按手机/邮箱「" + owner + "」找不到成员");
                } else if (user.status() == null || user.status() != 1) {
                    sink.addError(row.rowNo(), "owner", ImportErrorCode.REFERENCE_NOT_FOUND,
                            "成员「" + owner + "」已停用，不能作为负责人");
                }
            }
        }
    }
}
