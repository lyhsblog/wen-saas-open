package com.wensaas.core.impl.permission.org;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /** 列租户所有部门（未软删；含停用），按 parent_id, sort_order, name 排序。 */
    List<SysDept> listForTenant(@Param("tenantId") long tenantId);

    /** 同 parent 下名字是否冲突（排除自身 selfId，新增传 null）。 */
    int countByNameAndParent(@Param("tenantId") long tenantId,
                              @Param("parentId") long parentId,
                              @Param("name") String name,
                              @Param("selfId") Long selfId);

    /** 该部门下直接子部门数。 */
    int countChildren(@Param("tenantId") long tenantId, @Param("deptId") long deptId);

    /** 该部门下成员数（含兼任）。 */
    int countMembers(@Param("tenantId") long tenantId, @Param("deptId") long deptId);

    /** 重写子树 ancestors：把所有 {@code ancestors LIKE oldPrefix%} 的行替换为 {@code newPrefix...}。 */
    int updateAncestorsPrefix(@Param("tenantId") long tenantId,
                               @Param("oldPrefix") String oldPrefix,
                               @Param("newPrefix") String newPrefix);
}
