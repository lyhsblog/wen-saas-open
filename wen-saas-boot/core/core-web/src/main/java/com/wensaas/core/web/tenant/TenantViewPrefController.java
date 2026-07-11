package com.wensaas.core.web.tenant;

import com.wensaas.core.impl.viewpref.ViewPrefService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 租户后台 · 用户级列表列偏好。
 *
 * <p>个人自助，主体取自登录态，无需额外权限点（与个人中心同惯例）。
 * {@code viewKey} 为列表标识（如 {@code crm_demo.customer}）。
 */
@Tag(name = "tenant-view-pref", description = "租户后台 · 列表列偏好")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/view-pref")
public class TenantViewPrefController {

    private static final int MAX_VIEW_KEY_LEN = 120;
    private static final int MAX_KEYS = 200;
    private static final int MAX_KEY_LEN = 80;

    private final ViewPrefService service;

    public TenantViewPrefController(ViewPrefService service) {
        this.service = service;
    }

    @Operation(summary = "读取列偏好", description = "无配置时 customized=false + 空数组")
    @GetMapping("/{viewKey}")
    public ViewPrefService.ViewPref get(@PathVariable String viewKey) {
        validateViewKey(viewKey);
        return service.get(viewKey);
    }

    @Operation(summary = "保存列偏好（upsert）")
    @PutMapping("/{viewKey}")
    public void save(@PathVariable String viewKey, @RequestBody SaveRequest req) {
        validateViewKey(viewKey);
        validateKeys(req.customKeys(), "customKeys");
        validateKeys(req.hiddenKeys(), "hiddenKeys");
        service.save(viewKey, req.customKeys(), req.hiddenKeys());
    }

    @Operation(summary = "重置列偏好（删行）")
    @DeleteMapping("/{viewKey}")
    public void reset(@PathVariable String viewKey) {
        validateViewKey(viewKey);
        service.reset(viewKey);
    }

    private static void validateViewKey(String viewKey) {
        if (viewKey == null || viewKey.isBlank() || viewKey.length() > MAX_VIEW_KEY_LEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "viewKey 非法");
        }
    }

    private static void validateKeys(List<String> keys, String field) {
        if (keys == null) {
            return;
        }
        if (keys.size() > MAX_KEYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 数量超上限 " + MAX_KEYS);
        }
        for (String k : keys) {
            if (k == null || k.length() > MAX_KEY_LEN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 含非法列 key");
            }
        }
    }

    public record SaveRequest(List<String> customKeys, List<String> hiddenKeys) {
    }
}
