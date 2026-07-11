package com.wensaas.core.api.audit;

import java.util.ArrayList;
import java.util.List;

/**
 * ② 多主对象「发射缓冲」（请求作用域 ThreadLocal）。
 *
 * <p>用途：一个请求操作了**多个**主对象时（如批量、导入逐行），业务在 service 里调
 * {@link #add(String, String, String)} 逐个登记；{@code AuditableAspect} 在收尾时
 * {@link #drain()} 取出合并进**这一行** ② 的 {@code object_refs}，按对象 id 可追溯。
 *
 * <p>不调用则不影响：单对象场景由写入侧从 {@code @Auditable} 的 objectType/objectId/objectDesc 派生 1 元素。
 *
 * <p>纪律：只在"业务事件真正涉及多对象"时登记，别在每个子步骤乱调（否则一行塞太多）。
 * 由 {@code RequestIdFilter} 在请求结束 {@link #clear()} 兜底清理，避免 ThreadLocal 跨请求泄漏。
 */
public final class AuditObjects {

    private static final ThreadLocal<List<AuditObjectRef>> CURRENT = new ThreadLocal<>();

    /** 单请求 object_refs 上限：防大批量（如导入上万行）把一行 ② 的 jsonb 撑爆；超出丢弃，计数仍可见于 desc。 */
    private static final int MAX_REFS = 500;

    private AuditObjects() {
    }

    /**
     * 登记一个发生改名的主对象：用 {@code 旧名/新名} 表达「这条数据先后有过两个名字」。
     *
     * <p>规则（空值忽略，对语义无帮助；细节字段变动靠 ③ 数据变更日志看）：
     * <ul>
     *   <li>两名都有且不同 → {@code "旧名/新名"}（如 {@code "财务部/财务一部"}）</li>
     *   <li>只有一个 / 两者相同（未真正改名）→ 单名</li>
     *   <li>两者皆空 → 仅登记 id（name 留空）</li>
     * </ul>
     * 注意：这里的 name 是纯展示用对象标识（不被外部引用、搜索走业务数据 id），故可承载组合名。
     */
    public static void add(String type, String id, String beforeName, String afterName) {
        add(type, id, combineNames(beforeName, afterName));
    }

    /** {@code 旧名/新名} 组合：见 {@link #add(String, String, String, String)} 规则。 */
    private static String combineNames(String beforeName, String afterName) {
        String before = (beforeName == null || beforeName.isBlank()) ? null : beforeName.trim();
        String after = (afterName == null || afterName.isBlank()) ? null : afterName.trim();
        if (before == null) {
            return after;
        }
        if (after == null || after.equals(before)) {
            return before;
        }
        return before + "/" + after;
    }

    /**
     * 登记一个本请求涉及的主对象（超过 {@link #MAX_REFS} 后丢弃）。
     *
     * @param type 实体类型 code（{@code EntityTypes.SYS_*}）；无对应实体则传 {@code null}，友好名仍由 {@code name} 承载
     */
    public static void add(String type, String id, String name) {
        if (id == null) {
            return;
        }
        List<AuditObjectRef> list = CURRENT.get();
        if (list == null) {
            list = new ArrayList<>(4);
            CURRENT.set(list);
        }
        if (list.size() >= MAX_REFS) {
            return;
        }
        list.add(new AuditObjectRef(type, id, name));
    }

    /** 取出并清空本请求登记的对象（切面收尾调用）。无登记返回空列表。 */
    public static List<AuditObjectRef> drain() {
        List<AuditObjectRef> list = CURRENT.get();
        CURRENT.remove();
        return list == null ? List.of() : list;
    }

    /** 请求结束兜底清理（无 @Auditable 的端点也不会泄漏到下个请求）。 */
    public static void clear() {
        CURRENT.remove();
    }
}
