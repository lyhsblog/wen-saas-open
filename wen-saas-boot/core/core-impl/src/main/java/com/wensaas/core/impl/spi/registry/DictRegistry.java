package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.def.DictGroupDef;
import org.springframework.stereotype.Component;

@Component
public class DictRegistry extends AbstractRegistry<DictGroupDef> {

    public DictRegistry() {
        super(DictGroupDef::code);
    }
}
