package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.def.DataPermissionTableDef;
import org.springframework.stereotype.Component;
import com.wensaas.core.impl.spi.module.BusinessModuleRegistrar;

/**
 * 数据权限参与表注册表（按表名小写做 key）。
 *
 * <p>由 {@link BusinessModuleRegistrar} 在启动期注册；运行时由
 * {@code DataPermissionHandlerImpl} 查询。
 */
@Component
public class DataPermissionTableRegistry extends AbstractRegistry<DataPermissionTableDef> {

    public DataPermissionTableRegistry() {
        super(def -> def.tableName().toLowerCase());
    }
}
