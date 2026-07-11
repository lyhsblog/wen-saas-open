package com.wensaas.core.impl.ops.health;

import com.wensaas.core.api.ops.setting.OpsSettingService;
import com.wensaas.core.api.ops.setting.SettingCategory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 健康度评分参数读取门面。
 *
 * <p>运营可在「系统参数 / 健康度」运行时调整风险信号权重、分档阈值与判定参数；
 * {@link HealthScoreCalculator} 统一经此门面读取。优先读 {@code sys_setting(category=health_score)}
 * （{@link OpsSettingService} 经统一 CacheRegion 缓存：进程内 L1 + Redis L2 + pub/sub 跨节点失效），
 * {@code OpsSettingService} 不可用时
 * （如纯计算单测）回退编译期默认值（与种子保持一致）。
 */
public class HealthScoreSettings {

    // 分档阈值
    static final String KEY_THRESHOLD_HEALTHY = "threshold_healthy";
    static final String KEY_THRESHOLD_WARNING = "threshold_warning";
    // 信号权重（正数扣分幅度）
    static final String KEY_W_NO_LOGIN = "weight_no_login_30d";
    static final String KEY_W_MAU_DROP = "weight_mau_drop";
    static final String KEY_W_EXPIRING = "weight_expiring_soon";
    static final String KEY_W_RENEWAL_GAP = "weight_renewal_gap";
    static final String KEY_W_URGENT_TICKET = "weight_urgent_ticket";
    static final String KEY_W_TICKET_VOLUME = "weight_ticket_volume";
    static final String KEY_W_ONLY_BASIC = "weight_only_basic";
    // 判定参数
    static final String KEY_NO_LOGIN_DAYS = "no_login_days";
    static final String KEY_EXPIRING_SOON_DAYS = "expiring_soon_days";
    static final String KEY_MAU_DROP_RATIO_PCT = "mau_drop_ratio_pct";
    static final String KEY_MAU_MIN_BASE = "mau_min_base";
    static final String KEY_TICKET_VOLUME_THRESHOLD = "ticket_volume_threshold";

    private final ObjectProvider<OpsSettingService> settingProvider;

    public HealthScoreSettings(ObjectProvider<OpsSettingService> settingProvider) {
        this.settingProvider = settingProvider;
    }

    public int healthyThreshold() { return getInt(KEY_THRESHOLD_HEALTHY, 80); }
    public int warningThreshold() { return getInt(KEY_THRESHOLD_WARNING, 50); }

    public int weightNoLogin() { return getInt(KEY_W_NO_LOGIN, 25); }
    public int weightMauDrop() { return getInt(KEY_W_MAU_DROP, 10); }
    public int weightExpiring() { return getInt(KEY_W_EXPIRING, 20); }
    public int weightRenewalGap() { return getInt(KEY_W_RENEWAL_GAP, 10); }
    public int weightUrgentTicket() { return getInt(KEY_W_URGENT_TICKET, 15); }
    public int weightTicketVolume() { return getInt(KEY_W_TICKET_VOLUME, 10); }
    public int weightOnlyBasic() { return getInt(KEY_W_ONLY_BASIC, 10); }

    public int noLoginDays() { return getInt(KEY_NO_LOGIN_DAYS, 30); }
    public int expiringSoonDays() { return getInt(KEY_EXPIRING_SOON_DAYS, 30); }
    public int mauDropRatioPct() { return getInt(KEY_MAU_DROP_RATIO_PCT, 70); }
    /** 月活下降判定最小基数：上月活跃成员数低于此值不计「月活下降」，避免小样本（如仅 1 人）误报 100% 降幅。 */
    public int mauMinBase() { return getInt(KEY_MAU_MIN_BASE, 3); }
    public int ticketVolumeThreshold() { return getInt(KEY_TICKET_VOLUME_THRESHOLD, 5); }

    private int getInt(String key, int dflt) {
        OpsSettingService s = settingProvider.getIfAvailable();
        if (s == null) {
            return dflt;
        }
        Integer v = s.get(SettingCategory.HEALTH_SCORE, key, Integer.class, dflt);
        return v != null ? v : dflt;
    }
}
