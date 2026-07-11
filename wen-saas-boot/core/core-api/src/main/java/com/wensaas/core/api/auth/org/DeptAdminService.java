package com.wensaas.core.api.auth.org;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 部门管理（写）API。
 *
 * <p>租户上下文：所有方法从 {@code TenantContext.require()} 取 {@code tenantId}。
 *
 * <p>{@code ancestors} 物化路径形如 {@code /1/3/15/}，最大深度 5；move 时整子树批量重写。
 */
public interface DeptAdminService {

    /** 返回当前租户的部门树（按 {@code sort_order, name} 排序，包含已停用）。 */
    List<DeptView> listTree();

    /** 返回单个部门详情。 */
    DeptView getDetail(long deptId);

    /** 创建子部门。{@code parentId=0} 表示一级部门。 */
    DeptView create(CreateDeptRequest req);

    /** 编辑部门基本信息（不含 parent）。 */
    DeptView update(long deptId, UpdateDeptRequest req);

    /** 软删部门。{@code DEPT_HAS_CHILDREN} / {@code DEPT_HAS_MEMBERS} 阻断。 */
    void delete(long deptId);

    /** 移动部门到新上级（含子树 ancestors 批量重写）。 */
    DeptView move(long deptId, long newParentId);

    /**
     * 同级批量重排：orderedIds 须是 parentId 下的全部直接子部门（parentId=0 表示一级），
     * 按其顺序整体重写 sort_order（0,1,2…），单事务完成。校验：归属租户、同一父级、集合一致。
     */
    void reorder(long parentId, List<Long> orderedIds);

    /** 启用 / 停用部门。 */
    DeptView setStatus(long deptId, boolean enabled);

    /** 列出部门负责人。 */
    List<DeptLeaderRef> listLeaders(long deptId);

    /** 设置部门负责人（全量替换）。每个 userId 必须是该部门成员（主或兼）。 */
    List<DeptLeaderRef> setLeaders(long deptId, List<Long> userIds);

    // ===================== records =====================

    record DeptLeaderRef(long userId, String username, String realName) {
    }

    record CreateDeptRequest(Long parentId, String name, Integer sortOrder) {
    }

    record UpdateDeptRequest(String name, Integer sortOrder) {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class DeptView {
        private long id;
        private long parentId;
        private String name;
        private String ancestors;
        private int sortOrder;
        private int status;
        private int memberCount;
        private List<DeptView> children;

    }
}
