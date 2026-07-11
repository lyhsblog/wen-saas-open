package com.wensaas.core.impl.viewpref;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户级列表列偏好服务。
 *
 * <p>作用域 {@code (tenant_id, user_id, view_key)}：tenant 由拦截器自动隔离，user 取
 * {@link UserContext}，view_key 由调用方传入（如 {@code crm_demo.customer}）。
 * 存增量、不存整表；重置 = 删行。三态合并（固定/可见/隐藏）由前端 {@code resolveColumns} 负责，
 * 后端仅持久化 customKeys / hiddenKeys 两个数组。
 */
@Service
public class ViewPrefService {

    private final SysUserViewPrefMapper mapper;

    public ViewPrefService(SysUserViewPrefMapper mapper) {
        this.mapper = mapper;
    }

    /** 读取当前用户在某列表的偏好；无配置时 {@code customized=false} + 空数组。 */
    public ViewPref get(String viewKey) {
        SysUserViewPref row = mapper.selectOne(byUserView(viewKey));
        if (row == null) {
            return new ViewPref(false, List.of(), List.of());
        }
        return new ViewPref(true, nz(row.getCustomKeys()), nz(row.getHiddenKeys()));
    }

    /** upsert 当前用户在某列表的偏好。 */
    public void save(String viewKey, List<String> customKeys, List<String> hiddenKeys) {
        List<String> custom = nz(customKeys);
        List<String> hidden = nz(hiddenKeys);
        LocalDateTime now = LocalDateTime.now();

        SysUserViewPref existing = mapper.selectOne(byUserView(viewKey));
        if (existing != null) {
            existing.setCustomKeys(custom);
            existing.setHiddenKeys(hidden);
            existing.setUpdatedAt(now);
            mapper.updateById(existing);
            return;
        }
        SysUserViewPref row = new SysUserViewPref();
        row.setTenantId(TenantContext.require());
        row.setUserId(UserContext.require().userId());
        row.setViewKey(viewKey);
        row.setCustomKeys(custom);
        row.setHiddenKeys(hidden);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        mapper.insert(row);
    }

    /** 重置（删行）。无配置时静默。 */
    public void reset(String viewKey) {
        mapper.delete(byUserView(viewKey));
    }

    private QueryWrapper<SysUserViewPref> byUserView(String viewKey) {
        // tenant_id 由 TenantLine 拦截器自动追加
        return new QueryWrapper<SysUserViewPref>()
                .eq("user_id", UserContext.require().userId())
                .eq("view_key", viewKey);
    }

    private static List<String> nz(List<String> v) {
        return v == null ? List.of() : v;
    }

    /** 列偏好读出契约。 */
    public record ViewPref(boolean customized, List<String> customKeys, List<String> hiddenKeys) {
    }
}
