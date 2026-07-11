package com.wensaas.core.api.spi.def;

/**
 * 字段关联存在性校验声明。
 *
 * <p>如成员导入的「部门名」字段声明 {@code new ReferenceDef("sys.dept", BY_NAME)}，
 * 引擎据此收集该列全部非空值，批量回调
 * {@code ImportValidationResolver.resolveReferences} 取数，未命中的值回写
 * {@code REFERENCE_NOT_FOUND}。
 *
 * @param entityCode 被关联的实体编码；字典项类目标约定填字典分组 code
 * @param matchBy    匹配方式（对取数实现与模板文档的约定性说明，引擎不解释）
 * @param label      错误文案中的目标名（如「上级部门」）；null 回退「关联对象」
 * @param inFileKey  同文件前向声明列 key：更早行该列的值视为已存在（自引用实体
 *                   「先建父再建子」用，如部门导入填 "name"）；null 表示仅认 DB
 */
public record ReferenceDef(String entityCode, ReferenceMatch matchBy, String label, String inFileKey) {

    /** 仅 DB 匹配、默认文案的简易构造。 */
    public ReferenceDef(String entityCode, ReferenceMatch matchBy) {
        this(entityCode, matchBy, null, null);
    }
}
