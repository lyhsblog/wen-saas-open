package com.wensaas.core.impl.customfield.def;

import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldException;
import com.wensaas.core.api.customfield.CustomFieldType;
import com.wensaas.core.api.dict.DictService;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 业务方写入端的字段值校验器。
 *
 * <ul>
 *   <li>类型校验：与 {@link CustomFieldType} 匹配（含 ENUM 真查 dict；ENUM 空白值视为未提供、跳过 dict 校验）</li>
 *   <li>必填校验：required=true 字段必须出现且非 null（ENUM 的空白串亦按未提供裁决必填）</li>
 *   <li>长度校验：TEXT ≤ 255 / LONG_TEXT ≤ 4000</li>
 *   <li>未声明字段：忽略（v2 可加严格模式）</li>
 * </ul>
 *
 * <p>{@link DictService} 通过 {@link ObjectProvider} 可选注入：字典框架未装配时跳过 dict 存在性校验，
 * 仅校验值为 String。
 */
public class CustomFieldValueValidator {

    private static final int TEXT_MAX_LENGTH = 255;
    private static final int LONG_TEXT_MAX_LENGTH = 4000;

    private final ObjectProvider<DictService> dictServiceProvider;

    public CustomFieldValueValidator(ObjectProvider<DictService> dictServiceProvider) {
        this.dictServiceProvider = dictServiceProvider;
    }

    /**
     * 按已知字段定义清单校验业务方提交的值 map。
     *
     * @param defs   字段定义清单
     * @param values 业务方提交（可为 null/empty，等同于"未提供任何字段值"）
     */
    public void validate(List<CustomFieldDef> defs, Map<String, Object> values) {
        Map<String, Object> safe = values == null ? Map.of() : values;
        for (CustomFieldDef def : defs) {
            Object v = safe.get(def.fieldCode());
            if (v == null || isBlankEnum(def, v)) {
                if (def.required()) {
                    throw new CustomFieldException(CustomFieldException.Code.INVALID_VALUE,
                            "字段 '" + def.fieldCode() + "' 必填但未提供值");
                }
                continue;
            }
            checkValue(def, v);
        }
    }

    /**
     * ENUM 字段的空白字符串视为「未提供」——前端 Select 提交前已剔空，这里防御 API 直连 / 清空场景：
     * 不再当作合法 item code 去查字典而误报 {@code ENUM_VALUE_NOT_IN_DICT}；必填与否仍由 {@link #validate} 统一裁决。
     */
    private static boolean isBlankEnum(CustomFieldDef def, Object value) {
        return def.type() == CustomFieldType.ENUM && value instanceof String s && s.isBlank();
    }

    /** 单字段单值校验（暴露公开方便单元测试与 DefManager 在 default_value 校验时复用）。 */
    public void checkValue(CustomFieldDef def, Object value) {
        switch (def.type()) {
            case TEXT -> {
                requireType(def, value, value instanceof String);
                requireLength(def, (String) value, TEXT_MAX_LENGTH);
            }
            case LONG_TEXT -> {
                requireType(def, value, value instanceof String);
                requireLength(def, (String) value, LONG_TEXT_MAX_LENGTH);
            }
            case NUMBER -> requireType(def, value, value instanceof Number);
            case BOOL -> requireType(def, value, value instanceof Boolean);
            case DATE -> checkDate(def, value);
            case ENUM -> checkEnum(def, value);
            case REF -> {
                // 接受 Long / 可无损转 Long 的 Number，或纯数字 String（雪花 ID 超 JS 数字精度，前端用字符串传）；
                // v1 不查目标对象存在
                if (value instanceof Number n) {
                    if (n instanceof Double || n instanceof Float) {
                        throw invalidValue(def, "REF 期望 Long，实际浮点数 " + value);
                    }
                } else if (value instanceof String s) {
                    if (!s.matches("\\d{1,19}")) {
                        throw invalidValue(def, "REF 期望 Long 或数字字符串，实际 " + s);
                    }
                } else {
                    throw invalidValue(def, "REF 期望 Long / 数字字符串，实际 " + value.getClass().getSimpleName());
                }
            }
        }
    }

    private static void checkDate(CustomFieldDef def, Object value) {
        if (value instanceof LocalDate || value instanceof LocalDateTime || value instanceof Date
                || value instanceof Long) {
            return;
        }
        if (value instanceof String s) {
            try {
                LocalDate.parse(s);
                return;
            } catch (DateTimeParseException ignored) {
                // try datetime
            }
            try {
                LocalDateTime.parse(s);
                return;
            } catch (DateTimeParseException e) {
                throw invalidValue(def, "DATE 字符串需 ISO-8601 格式（YYYY-MM-DD 或 YYYY-MM-DDTHH:mm:ss）: " + s);
            }
        }
        throw invalidValue(def, "DATE 仅接受 LocalDate/LocalDateTime/Date/Long/ISO-String，实际 "
                + value.getClass().getSimpleName());
    }

    private void checkEnum(CustomFieldDef def, Object value) {
        if (!(value instanceof String code)) {
            throw invalidValue(def, "ENUM 期望 String item code，实际 " + value.getClass().getSimpleName());
        }
        if (code.isBlank()) {
            return; // 空白视为未提供：跳过字典存在性校验（必填由 validate 统一拦；防御 checkValue 直连/默认值路径）
        }
        DictService dictService = dictServiceProvider.getIfAvailable();
        if (dictService == null) {
            return; // 字典框架未装配：跳过 dict 存在性校验
        }
        String groupCode = def.refDictCode();
        if (groupCode == null || groupCode.isBlank()) {
            throw new CustomFieldException(CustomFieldException.Code.MISSING_REF_CODE,
                    "ENUM 字段 '" + def.fieldCode() + "' 缺 refDictCode");
        }
        Map<String, String> names = dictService.batchGetItemNames(groupCode, List.of(code));
        if (!names.containsKey(code)) {
            throw new CustomFieldException(CustomFieldException.Code.ENUM_VALUE_NOT_IN_DICT,
                    "字段 '" + def.fieldCode() + "' 的值 '" + code
                            + "' 不在字典分组 " + groupCode + " 中");
        }
    }

    /** 校验 ENUM 字段定义引用的字典分组存在（DefManager 在 createField 时调用）。 */
    public void ensureDictGroupExists(String dictGroupCode) {
        DictService dictService = dictServiceProvider.getIfAvailable();
        if (dictService == null) {
            return; // 字典框架未装配：DefManager 自行决定是否阻止 ENUM 创建
        }
        Optional<?> g = dictService.getGroup(dictGroupCode);
        if (g.isEmpty()) {
            throw new CustomFieldException(CustomFieldException.Code.DICT_GROUP_NOT_FOUND,
                    "字典分组 " + dictGroupCode + " 不存在");
        }
    }

    /** 把 List 化为按 fieldCode 索引的 Map，方便上层调用前预处理。 */
    public static Map<String, CustomFieldDef> indexByCode(Collection<CustomFieldDef> defs) {
        Map<String, CustomFieldDef> map = new HashMap<>(defs.size() * 2);
        for (CustomFieldDef def : defs) {
            map.put(def.fieldCode(), def);
        }
        return map;
    }

    private static void requireType(CustomFieldDef def, Object value, boolean ok) {
        if (!ok) {
            throw invalidValue(def, "期望 " + def.type() + "，实际 " + value.getClass().getSimpleName());
        }
    }

    private static void requireLength(CustomFieldDef def, String value, int max) {
        if (value.length() > max) {
            throw invalidValue(def, "字段 '" + def.fieldCode() + "' 长度 " + value.length()
                    + " 超过上限 " + max);
        }
    }

    private static CustomFieldException invalidValue(CustomFieldDef def, String msg) {
        return new CustomFieldException(CustomFieldException.Code.INVALID_VALUE,
                "字段 '" + def.fieldCode() + "': " + msg);
    }
}
