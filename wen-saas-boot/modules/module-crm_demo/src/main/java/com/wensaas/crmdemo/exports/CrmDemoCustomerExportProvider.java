package com.wensaas.crmdemo.exports;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldService;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.exports.spi.ExportColumn;
import com.wensaas.core.api.exports.spi.ExportDataProvider;
import com.wensaas.core.api.exports.spi.ExportSink;
import com.wensaas.crmdemo.entity.CrmDemoCustomer;
import com.wensaas.crmdemo.entity.CustomerStatus;
import com.wensaas.crmdemo.repository.CrmDemoCustomerMapper;
import com.wensaas.crmdemo.service.CrmDemoCustomerService;
import com.wensaas.crmdemo.spi.CrmDemoModule;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 客户导出取数（{@code ExportDataProvider}）。列 = 标准字段 + 该租户的自定义字段（动态展开，
 * 内建三个 provider 未演示过的形态）；行数据复用 {@code service.page}（负责人翻名、自定义字段
 * enum/ref 的 {@code __label} 视图都现成）。运行在申请人 {@code UserContext} 内，行级可见性
 * 由数据权限拦截器按申请人 data_scope 自动叠加（所见即所得）。xlsx 写出由框架承担。
 */
@Component
public class CrmDemoCustomerExportProvider implements ExportDataProvider {

    private static final int PAGE_SIZE = 500;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CrmDemoCustomerService customerService;
    private final CrmDemoCustomerMapper customerMapper;
    private final CustomFieldService customFieldService;
    private final DictService dictService;

    public CrmDemoCustomerExportProvider(CrmDemoCustomerService customerService,
                                         CrmDemoCustomerMapper customerMapper,
                                         CustomFieldService customFieldService,
                                         DictService dictService) {
        this.customerService = customerService;
        this.customerMapper = customerMapper;
        this.customFieldService = customFieldService;
        this.dictService = dictService;
    }

    @Override
    public String objectCode() {
        return CrmDemoModule.EXPORT_CUSTOMER;
    }

    @Override
    public String requiredPermission() {
        // 导出全量客户列表属管理视图能力（行级范围仍按申请人 data_scope 所见即所得）；
        // 「我的客户」的个人导出走独立对象 crm_demo_customer_mine（见 CrmDemoCustomerMineExportProvider）
        return "crm_demo.customer.view_manage";
    }

    @Override
    public List<ExportColumn> columns() {
        List<ExportColumn> cols = new ArrayList<>(List.of(
                new ExportColumn("name", "客户名称"),
                new ExportColumn("code", "客户编号"),
                new ExportColumn("status", "阶段"),
                new ExportColumn("source", "来源"),
                new ExportColumn("owner", "负责人"),
                new ExportColumn("createdAt", "创建时间")));
        // 自定义字段动态展开为列（按租户当前定义；申请→执行窗口内字段变更以执行时为准）
        for (CustomFieldDef def : customFieldService.listFields(CrmDemoModule.OBJECT_CUSTOMER)) {
            cols.add(new ExportColumn(def.fieldCode(), def.fieldName()));
        }
        return cols;
    }

    @Override
    public long estimateCount(Map<String, String> filters) {
        QueryWrapper<CrmDemoCustomer> w = new QueryWrapper<>();
        String status = filters.get("status");
        if (status != null && !status.isBlank()) {
            w.eq("status", status);
        }
        String source = filters.get("source");
        if (source != null && !source.isBlank()) {
            w.eq("source", source);
        }
        String keyword = filters.get("keyword");
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            w.and(x -> x.like("name", kw).or().like("code", kw));
        }
        Long ownerId = ownerIdOf(filters);
        if (ownerId != null) {
            w.eq("owner_id", ownerId);
        }
        // cf_* 列由 CustomFieldQueryRewriter 翻译为 extra->>'cf_x'（与列表查询同一守卫：仅索引化字段允许）
        cfEqualsOf(filters).forEach(w::eq);
        Long count = customerMapper.selectCount(w);
        return count == null ? 0 : count;
    }

    @Override
    public void export(Map<String, String> filters, ExportSink sink) {
        List<CustomFieldDef> cfDefs = customFieldService.listFields(CrmDemoModule.OBJECT_CUSTOMER);
        Map<String, String> sourceNames = new java.util.HashMap<>();
        for (DictItem it : dictService.listItems(CrmDemoModule.DICT_CUSTOMER_SOURCE)) {
            if (it.code() != null && it.name() != null) {
                sourceNames.put(it.code(), it.name());
            }
        }
        long page = 1;
        while (!sink.stopped()) {
            // ownerId / cf_*（自定义字段等值）与列表查询同源透传，保证导出范围与列表所见一致
            CrmDemoCustomerService.PageResult result = customerService.page(new CrmDemoCustomerService.Query(
                    blank(filters.get("status")), blank(filters.get("keyword")), ownerIdOf(filters),
                    blank(filters.get("source")), cfEqualsOf(filters), page, PAGE_SIZE));
            if (result.records().isEmpty()) {
                return;
            }
            for (CrmDemoCustomerService.ListItem item : result.records()) {
                if (sink.stopped()) {
                    return;
                }
                sink.row(toCells(item, cfDefs, sourceNames));
            }
            if (result.records().size() < PAGE_SIZE) {
                return;
            }
            page++;
        }
    }

    private static List<String> toCells(CrmDemoCustomerService.ListItem item, List<CustomFieldDef> cfDefs,
                                        Map<String, String> sourceNames) {
        CustomerStatus status = CustomerStatus.parse(item.status());
        List<String> cells = new ArrayList<>(6 + cfDefs.size());
        cells.add(item.name());
        cells.add(item.code());
        cells.add(status == null ? item.status() : status.label());
        cells.add(item.source() == null ? null : sourceNames.getOrDefault(item.source(), item.source()));
        cells.add(item.ownerName() != null ? item.ownerName()
                : item.ownerId() == null ? null : String.valueOf(item.ownerId()));
        cells.add(item.createdAt() == null ? null : item.createdAt().format(TIME_FMT));
        Map<String, Object> extra = item.extra() == null ? Map.of() : item.extra();
        for (CustomFieldDef def : cfDefs) {
            // enum/ref 优先取展示名（__label 衍生键），否则原值
            Object label = extra.get(def.fieldCode() + "__label");
            Object raw = label != null ? label : extra.get(def.fieldCode());
            cells.add(raw == null ? null : String.valueOf(raw));
        }
        return cells;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** filters.ownerId → Long（管理列表「归属人」筛选、我的客户强制本人 两处消费）；缺失或非法返回 null。 */
    private static Long ownerIdOf(Map<String, String> filters) {
        String raw = filters.get("ownerId");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** filters 中 cf_ 前缀键 → 自定义字段等值条件（键形态与 @CustomFieldEquals 收集口径一致）。 */
    private static Map<String, Object> cfEqualsOf(Map<String, String> filters) {
        Map<String, Object> out = new java.util.HashMap<>();
        filters.forEach((k, v) -> {
            if (k != null && k.startsWith("cf_") && v != null && !v.isBlank()) {
                out.put(k, v);
            }
        });
        return out;
    }
}
