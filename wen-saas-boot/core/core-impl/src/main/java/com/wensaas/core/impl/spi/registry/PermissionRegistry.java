package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.def.PermissionDef;
import org.springframework.stereotype.Component;

@Component
public class PermissionRegistry extends AbstractRegistry<PermissionDef> {

    public PermissionRegistry() {
        super(PermissionDef::code);
    }
}
