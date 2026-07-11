package com.wensaas.core.impl.spi.module;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.ModuleMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 把启动期发现的业务模块同步到 {@code sys_business_module} 表。
 *
 * <p>同步策略（幂等增量：以 code 为键只增改、从不删行，避免误删仍被套餐/权限引用的模块记录）：
 * <ul>
 *   <li>本次未出现的模块记录 → 不动</li>
 *   <li>不存在 → INSERT</li>
 *   <li>version 不同 → UPDATE 全字段 + updated_at；SemVer 比较得出升级 / 回退 → INFO / WARN 日志</li>
 *   <li>version 相同 → 仍把 name/description/icon 对齐为代码 metadata（代码是权威，改文案无需升 version），
 *       仅当有差异时才写库以免每次启动空刷 updated_at</li>
 * </ul>
 */
@Component
public class BusinessModuleDbSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(BusinessModuleDbSynchronizer.class);

    private final SysBusinessModuleMapper mapper;

    public BusinessModuleDbSynchronizer(SysBusinessModuleMapper mapper) {
        this.mapper = mapper;
    }

    public void sync(List<BusinessModuleDescriptor> enabledModules) {
        for (BusinessModuleDescriptor d : enabledModules) {
            syncOne(d.metadata());
        }
    }

    private void syncOne(ModuleMetadata metadata) {
        SysBusinessModule existing = mapper.selectOne(
                new LambdaQueryWrapper<SysBusinessModule>().eq(SysBusinessModule::getCode, metadata.code()));

        if (existing == null) {
            SysBusinessModule row = new SysBusinessModule();
            row.setCode(metadata.code());
            row.setName(metadata.name());
            row.setVersion(metadata.version());
            row.setDescription(metadata.description());
            row.setIcon(metadata.icon());
            mapper.insert(row);
            log.info("[SPI] 模块注册 {}: version={}", metadata.code(), metadata.version());
            return;
        }

        boolean versionChanged = !metadata.version().equals(existing.getVersion());
        if (!versionChanged) {
            // version 未变也把代码侧 name/description/icon 同步为权威值（改文案无需升 version）；无差异不写库。
            boolean metaChanged = !Objects.equals(metadata.name(), existing.getName())
                    || !Objects.equals(metadata.description(), existing.getDescription())
                    || !Objects.equals(metadata.icon(), existing.getIcon());
            if (metaChanged) {
                existing.setName(metadata.name());
                existing.setDescription(metadata.description());
                existing.setIcon(metadata.icon());
                existing.setUpdatedAt(LocalDateTime.now());
                mapper.updateById(existing);
                log.info("[SPI] 模块元数据同步 {}（version 未变）", metadata.code());
            }
            return;
        }

        int cmp = compareSemVer(metadata.version(), existing.getVersion());
        if (cmp > 0) {
            log.info("[SPI] 模块升级 {}: {} → {}", metadata.code(), existing.getVersion(), metadata.version());
        } else if (cmp < 0) {
            log.warn("[SPI] 模块版本回退 {}: {} → {}（保留不阻断启动）", metadata.code(), existing.getVersion(), metadata.version());
        } else {
            log.warn("[SPI] 模块版本字符串变化但 SemVer 解析等价 {}: {} → {}", metadata.code(), existing.getVersion(), metadata.version());
        }

        existing.setVersion(metadata.version());
        existing.setName(metadata.name());
        existing.setDescription(metadata.description());
        existing.setIcon(metadata.icon());
        existing.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(existing);
    }

    /**
     * SemVer 主.次.补丁 比较。非纯数字段位 fallback 到字符串比较并打一行 WARN。
     */
    static int compareSemVer(String a, String b) {
        int[] av = parseSemVer(a);
        int[] bv = parseSemVer(b);
        if (av == null || bv == null) {
            log.warn("[SPI] 版本号无法按 SemVer 解析，fallback 字符串比较: '{}' vs '{}'", a, b);
            return a.compareTo(b);
        }
        for (int i = 0; i < 3; i++) {
            if (av[i] != bv[i]) {
                return Integer.compare(av[i], bv[i]);
            }
        }
        return 0;
    }

    private static int[] parseSemVer(String v) {
        if (v == null) return null;
        // 去掉构建元数据 / 预发布后缀
        String main = v.split("[-+]", 2)[0];
        String[] parts = main.split("\\.");
        if (parts.length == 0 || parts.length > 3) return null;
        int[] out = new int[3];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }
}
