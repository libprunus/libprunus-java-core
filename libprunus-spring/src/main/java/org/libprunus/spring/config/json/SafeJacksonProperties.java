package org.libprunus.spring.config.json;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "prunus.json")
public record SafeJacksonProperties(@DefaultValue List<String> allowedPackages) {

    public SafeJacksonProperties {
        // Defensive copy: keep the bound collection unmodifiable and unaliased so the
        // record stays a true immutable value carrier (convention for upstream config records).
        allowedPackages = List.copyOf(allowedPackages);
    }
}
