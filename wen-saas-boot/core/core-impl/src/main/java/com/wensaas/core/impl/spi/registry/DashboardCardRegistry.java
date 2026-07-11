package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.def.DashboardCardDef;
import org.springframework.stereotype.Component;

@Component
public class DashboardCardRegistry extends AbstractRegistry<DashboardCardDef> {

    public DashboardCardRegistry() {
        super(DashboardCardDef::code);
    }
}
