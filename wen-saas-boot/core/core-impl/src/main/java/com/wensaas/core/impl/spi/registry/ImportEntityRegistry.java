package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.def.ImportEntityDef;
import org.springframework.stereotype.Component;

@Component
public class ImportEntityRegistry extends AbstractRegistry<ImportEntityDef> {

    public ImportEntityRegistry() {
        super(ImportEntityDef::code);
    }
}
