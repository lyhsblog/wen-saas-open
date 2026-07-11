package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.def.NotificationTemplateDef;
import org.springframework.stereotype.Component;

@Component
public class NotificationTemplateRegistry extends AbstractRegistry<NotificationTemplateDef> {

    public NotificationTemplateRegistry() {
        super(NotificationTemplateDef::code);
    }
}
