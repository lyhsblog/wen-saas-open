package com.wensaas.core.impl.imports;

import com.wensaas.core.api.imports.ImportErrorCode;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.imports.NormalizedRow;
import com.wensaas.core.api.imports.PreviewRow;
import com.wensaas.core.api.imports.RowError;
import com.wensaas.core.api.spi.def.FieldType;
import com.wensaas.core.api.spi.def.ImportEntityDef;
import com.wensaas.core.api.spi.def.ImportFieldDef;
import com.wensaas.core.api.spi.def.ReferenceDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 全量预校验引擎。
 *
 * <p>底座负责：必填 / 类型 / 格式 / 文件内唯一（声明式，纯本地判定），以及按字段声明
 * （{@code UniqueScope.checksInDb()} / {@code ImportFieldDef.reference()}）驱动的批量 DB 校验——
 * 引擎收集列值、回调 {@link ImportValidationResolver} 取数口、逐行回写
 * {@code DUPLICATE_IN_DB} / {@code REFERENCE_NOT_FOUND}；引擎自身不触库。
 * 声明覆盖不了的业务规则由 {@link ImportValidationResolver#validate} 补充（无 resolver 时跳过）。
 *
 * <p>无状态；由 {@link ImportConfiguration} 装配（与任务中心同条件）。
 */
public class ImportValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(ImportValidationEngine.class);

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd";

    /** 跑全量校验，产出每行预览（含错误）+ 关联 id 映射。入参 {@code resolver} 可空。 */
    public ImportValidationOutcome validate(ImportEntityDef def, List<NormalizedRow> rows,
                                            ImportValidationResolver resolver) {
        Map<Integer, List<RowError>> errorsByRow = new HashMap<>();
        // 文件内唯一：字段 key -> 已见值集合
        Map<String, Set<String>> seen = new HashMap<>();

        for (NormalizedRow row : rows) {
            for (ImportFieldDef f : def.fields()) {
                String value = row.get(f.key());
                if (value.isEmpty()) {
                    if (f.required()) {
                        add(errorsByRow, row.rowNo(), f.key(), ImportErrorCode.REQUIRED_MISSING, "必填项不能为空");
                    }
                    continue;
                }
                if (!checkType(f, value)) {
                    add(errorsByRow, row.rowNo(), f.key(), ImportErrorCode.TYPE_INVALID,
                            "数据格式不符合类型 " + f.type());
                    continue;
                }
                if (f.type() != FieldType.DATE && f.format() != null && !matchesFormat(f.format(), value)) {
                    add(errorsByRow, row.rowNo(), f.key(), ImportErrorCode.FORMAT_INVALID, "格式不正确");
                }
                if (f.unique().checksInFile()
                        && !seen.computeIfAbsent(f.key(), k -> new HashSet<>()).add(value)) {
                    add(errorsByRow, row.rowNo(), f.key(), ImportErrorCode.DUPLICATE_IN_FILE, "文件内重复");
                }
            }
        }

        Map<String, Map<String, Long>> referenceIds =
                validateAgainstDb(def, rows, resolver, errorsByRow);

        if (resolver != null) {
            resolver.validate(rows, (rowNo, field, code, message) ->
                    add(errorsByRow, rowNo, field, code, message));
        }

        List<PreviewRow> result = new ArrayList<>(rows.size());
        for (NormalizedRow row : rows) {
            List<RowError> errs = errorsByRow.getOrDefault(row.rowNo(), List.of());
            result.add(new PreviewRow(row.rowNo(), row.excelRow(), row.cells(), errs.isEmpty(), errs));
        }
        return new ImportValidationOutcome(result, referenceIds);
    }

    /**
     * 声明驱动的批量 DB 校验：按字段收集非空值，回调 resolver 取数口做 IN_DB 查重与关联匹配。
     * 空值不参与（必填另判）；取数口返回 null（未实现）或无 resolver 时跳过并 warn。
     */
    private Map<String, Map<String, Long>> validateAgainstDb(ImportEntityDef def, List<NormalizedRow> rows,
                                                             ImportValidationResolver resolver,
                                                             Map<Integer, List<RowError>> errorsByRow) {
        Map<String, Map<String, Long>> referenceIds = new HashMap<>();
        for (ImportFieldDef f : def.fields()) {
            boolean checksDb = f.unique().checksInDb();
            ReferenceDef ref = f.reference();
            if (!checksDb && ref == null) {
                continue;
            }
            if (resolver == null) {
                log.warn("import.db-check.skipped entity={} field={} 声明了 DB 校验但无 resolver", def.code(), f.key());
                continue;
            }
            Set<String> values = new LinkedHashSet<>();
            for (NormalizedRow row : rows) {
                String v = row.get(f.key());
                if (!v.isEmpty()) {
                    values.add(v);
                }
            }
            if (values.isEmpty()) {
                continue;
            }
            if (checksDb) {
                Set<String> existing = resolver.existingDbValues(f.key(), values);
                if (existing == null) {
                    log.warn("import.db-check.unimplemented entity={} field={} existingDbValues 未实现",
                            def.code(), f.key());
                } else if (!existing.isEmpty()) {
                    for (NormalizedRow row : rows) {
                        String v = row.get(f.key());
                        if (!v.isEmpty() && existing.contains(v)) {
                            add(errorsByRow, row.rowNo(), f.key(), ImportErrorCode.DUPLICATE_IN_DB,
                                    f.header() + "已存在: " + v);
                        }
                    }
                }
            }
            if (ref != null) {
                Map<String, Long> resolved = resolver.resolveReferences(f, values);
                if (resolved == null) {
                    log.warn("import.db-check.unimplemented entity={} field={} resolveReferences 未实现",
                            def.code(), f.key());
                    continue;
                }
                referenceIds.put(f.key(), resolved);
                String label = ref.label() != null ? ref.label() : "关联对象";
                // 前向声明：更早行 inFileKey 列的值视为已存在（行处理完再登记 → 自引用自身不通过）
                Set<String> forwardDeclared = ref.inFileKey() == null ? null : new HashSet<>();
                for (NormalizedRow row : rows) {
                    String v = row.get(f.key());
                    if (!v.isEmpty() && !resolved.containsKey(v)
                            && (forwardDeclared == null || !forwardDeclared.contains(v))) {
                        add(errorsByRow, row.rowNo(), f.key(), ImportErrorCode.REFERENCE_NOT_FOUND,
                                label + "不存在: " + v);
                    }
                    if (forwardDeclared != null) {
                        String declared = row.get(ref.inFileKey());
                        if (!declared.isEmpty()) {
                            forwardDeclared.add(declared);
                        }
                    }
                }
            }
        }
        return referenceIds;
    }

    private boolean checkType(ImportFieldDef f, String value) {
        return switch (f.type()) {
            case INTEGER -> isInteger(value);
            case NUMBER -> isNumber(value);
            case BOOLEAN -> isBoolean(value);
            case DATE -> isDate(value, f.format());
            case STRING, DICT -> true;
        };
    }

    private static boolean isInteger(String v) {
        try {
            Long.parseLong(v.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isNumber(String v) {
        try {
            new BigDecimal(v.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isBoolean(String v) {
        return switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "是", "否", "true", "false", "1", "0", "y", "n", "yes", "no" -> true;
            default -> false;
        };
    }

    private static boolean isDate(String v, String pattern) {
        String p = (pattern == null || pattern.isBlank()) ? DEFAULT_DATE_PATTERN : pattern;
        try {
            DateTimeFormatter.ofPattern(p).parse(v.trim());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean matchesFormat(String format, String value) {
        return switch (format) {
            case "PHONE" -> PHONE.matcher(value).matches();
            case "EMAIL" -> EMAIL.matcher(value).matches();
            default -> safeRegexMatch(format, value);
        };
    }

    private static boolean safeRegexMatch(String regex, String value) {
        try {
            return Pattern.matches(regex, value);
        } catch (RuntimeException e) {
            return true; // 非法正则不应误杀数据
        }
    }

    private static void add(Map<Integer, List<RowError>> map, int rowNo, String field,
                            ImportErrorCode code, String message) {
        map.computeIfAbsent(rowNo, k -> new ArrayList<>()).add(new RowError(rowNo, field, code, message));
    }
}
