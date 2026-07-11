package com.wensaas.core.impl.stickynote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户级备忘便利贴服务。
 *
 * <p>作用域 {@code (tenant_id, user_id)}：tenant 由 {@link com.wensaas.core.impl.tenant.isolation
 * .TenantLineHandlerImpl} 自动隔离，user 取 {@link UserContext}。每个用户只见/只改自己的便签——
 * 改/删/置顶全部带 {@code user_id = self} 守卫（{@code updateById/deleteById} 只被拦截器补
 * tenant_id、不补 user_id，直接用会同租户跨用户越权）。
 *
 * <p>列表按 {@code updated_at} 倒序：最近新建或编辑的排最前，无手工排序。前端在会话内冻结
 * 展示顺序（编辑当下不即时冒顶），重新进入页面 / 刷新时才按本顺序重排，避免编辑跳位。
 */
@Service
public class StickyNoteService {

    private final StickyNoteMapper mapper;

    public StickyNoteService(StickyNoteMapper mapper) {
        this.mapper = mapper;
    }

    /** 当前用户的全部便签，按 updatedAt 倒序（同刻按 id 倒序）：最近新建/编辑的排最前。 */
    public List<Note> list() {
        List<StickyNote> rows = mapper.selectList(
                byUser().orderByDesc("updated_at").orderByDesc("id"));
        return rows.stream().map(StickyNoteService::toNote).toList();
    }

    /** 新建便签（updatedAt=now 使其排在最前）。 */
    public Note create(String content, String color) {
        LocalDateTime now = LocalDateTime.now();
        StickyNote row = new StickyNote();
        row.setTenantId(TenantContext.require());
        row.setUserId(UserContext.require().userId());
        row.setContent(content);
        row.setColor(color);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        mapper.insert(row);
        return toNote(row);
    }

    /** 改内容/颜色（刷新 updatedAt 冒顶）；不属于当前用户或不存在时返回 null。 */
    public Note update(Long id, String content, String color) {
        int rows = mapper.update(null, new UpdateWrapper<StickyNote>()
                .eq("id", id)
                .eq("user_id", UserContext.require().userId())
                .set("content", content)
                .set("color", color)
                .set("updated_at", LocalDateTime.now()));
        if (rows == 0) {
            return null;
        }
        StickyNote row = mapper.selectOne(byUser().eq("id", id));
        return row == null ? null : toNote(row);
    }

    /** 删除；返回是否删掉一行（不属于当前用户或不存在时 false）。 */
    public boolean delete(Long id) {
        return mapper.delete(byUser().eq("id", id)) > 0;
    }

    private QueryWrapper<StickyNote> byUser() {
        // tenant_id 由 TenantLine 拦截器自动追加
        return new QueryWrapper<StickyNote>().eq("user_id", UserContext.require().userId());
    }

    private static Note toNote(StickyNote r) {
        return new Note(r.getId(), r.getContent(), r.getColor(), r.getCreatedAt(), r.getUpdatedAt());
    }

    /** 便签读出契约。 */
    public record Note(Long id, String content, String color,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
