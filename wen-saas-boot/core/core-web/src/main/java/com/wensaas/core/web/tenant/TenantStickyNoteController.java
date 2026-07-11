package com.wensaas.core.web.tenant;

import com.wensaas.core.impl.stickynote.StickyNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * 租户后台 · 用户级备忘便利贴。
 *
 * <p>个人自助，主体取自登录态，无需额外权限点（与个人中心 / 列偏好同惯例）。
 * 每个用户维护自己的一批便签，改/删/排序均只作用于本人数据。
 */
@Tag(name = "tenant-sticky-note", description = "租户后台 · 备忘便利贴")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/sticky-notes")
public class TenantStickyNoteController {

    private static final int MAX_CONTENT_LEN = 2000;
    /** 前端小色板，服务端只做白名单校验。 */
    private static final Set<String> COLORS = Set.of("yellow", "green", "blue", "pink", "purple");

    private final StickyNoteService service;

    public TenantStickyNoteController(StickyNoteService service) {
        this.service = service;
    }

    @Operation(summary = "便签列表", description = "当前用户全部便签，按顺序升序")
    @GetMapping
    public List<StickyNoteService.Note> list() {
        return service.list();
    }

    @Operation(summary = "新建便签")
    @PostMapping
    public StickyNoteService.Note create(@RequestBody NoteRequest req) {
        String content = normalizeContent(req.content());
        String color = normalizeColor(req.color());
        return service.create(content, color);
    }

    @Operation(summary = "编辑便签（内容 / 颜色）")
    @PutMapping("/{id}")
    public StickyNoteService.Note update(@PathVariable Long id, @RequestBody NoteRequest req) {
        String content = normalizeContent(req.content());
        String color = normalizeColor(req.color());
        StickyNoteService.Note note = service.update(id, content, color);
        if (note == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "便签不存在");
        }
        return note;
    }

    @Operation(summary = "删除便签")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        if (!service.delete(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "便签不存在");
        }
    }

    private static String normalizeContent(String content) {
        String c = content == null ? "" : content.strip();
        if (c.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "便签内容不能为空");
        }
        if (c.length() > MAX_CONTENT_LEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "便签内容超上限 " + MAX_CONTENT_LEN);
        }
        return c;
    }

    private static String normalizeColor(String color) {
        if (color == null || color.isBlank()) {
            return "yellow";
        }
        if (!COLORS.contains(color)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "颜色非法");
        }
        return color;
    }

    public record NoteRequest(String content, String color) {
    }
}
