package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.spi.def.FieldType;
import com.wensaas.core.api.spi.def.ImportEntityDef;
import com.wensaas.core.api.spi.def.ImportFieldDef;
import com.wensaas.core.api.spi.def.ReferenceDef;
import com.wensaas.core.api.spi.def.ReferenceMatch;
import com.wensaas.core.api.spi.def.UniqueScope;

import java.util.List;

/**
 * 底座内置导入实体定义（部门 / 成员 / 字典）。
 *
 * <p>字段表头为模板中文表头，{@code key} 为 handler/resolver 取值键。
 */
public final class SysImportEntities {

    public static final String DICT_CODE = "sys.dict";
    public static final String DICT_HANDLER = "sys.task.import.dict";
    public static final String DEPT_CODE = "sys.dept";
    public static final String DEPT_HANDLER = "sys.task.import.dept";
    public static final String MEMBER_CODE = "sys.member";
    public static final String MEMBER_HANDLER = "sys.task.import.member";

    private SysImportEntities() {
    }

    /** 部门导入：上级部门按名关联（DB 已存在或同文件更早行已声明——先父后子）。 */
    public static ImportEntityDef dept() {
        return ImportEntityDef.of(DEPT_CODE, "部门", DEPT_HANDLER, List.of(
                new ImportFieldDef("部门名", "name", FieldType.STRING, true,
                        null, UniqueScope.IN_FILE, null, "销售部"),
                new ImportFieldDef("上级部门名", "parentName", FieldType.STRING, false,
                        null, UniqueScope.NONE,
                        new ReferenceDef(DEPT_CODE, ReferenceMatch.BY_NAME, "上级部门", "name"), "华东大区"),
                new ImportFieldDef("排序", "sortOrder", FieldType.INTEGER, false,
                        null, UniqueScope.NONE, null, "1")));
    }

    /** 成员导入：所属部门按名关联（须 DB 已存在）；账号 DB 唯一由执行期兜底。 */
    public static ImportEntityDef member() {
        return ImportEntityDef.of(MEMBER_CODE, "成员", MEMBER_HANDLER, List.of(
                new ImportFieldDef("账号", "username", FieldType.STRING, true,
                        null, UniqueScope.IN_FILE, null, "zhangsan"),
                new ImportFieldDef("姓名", "realName", FieldType.STRING, true,
                        null, UniqueScope.NONE, null, "张三"),
                new ImportFieldDef("手机", "mobile", FieldType.STRING, false,
                        "PHONE", UniqueScope.NONE, null, "13800138000"),
                new ImportFieldDef("邮箱", "email", FieldType.STRING, false,
                        "EMAIL", UniqueScope.NONE, null, "zhangsan@example.com"),
                new ImportFieldDef("部门", "deptName", FieldType.STRING, true,
                        null, UniqueScope.NONE,
                        new ReferenceDef(DEPT_CODE, ReferenceMatch.BY_NAME, "部门", null), "销售部")));
    }

    /** 字典项导入：按分组追加字典项。 */
    public static ImportEntityDef dict() {
        return ImportEntityDef.of(DICT_CODE, "字典项", DICT_HANDLER, List.of(
                new ImportFieldDef("分组编码", "groupCode", FieldType.STRING, true,
                        null, UniqueScope.NONE,
                        new ReferenceDef("sys.dictGroup", ReferenceMatch.BY_CODE, "字典分组", null), "user_status"),
                new ImportFieldDef("项编码", "code", FieldType.STRING, true,
                        null, UniqueScope.IN_FILE, null, "active"),
                new ImportFieldDef("显示名", "name", FieldType.STRING, true,
                        null, UniqueScope.NONE, null, "启用"),
                new ImportFieldDef("排序", "sortOrder", FieldType.INTEGER, false,
                        null, UniqueScope.NONE, null, "1")));
    }
}
