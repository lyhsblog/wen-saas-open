package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.def.H5PageDef;
import org.springframework.stereotype.Component;

@Component
public class H5PageRegistry extends AbstractRegistry<H5PageDef> {

    public H5PageRegistry() {
        super(H5PageDef::route);
    }
}
