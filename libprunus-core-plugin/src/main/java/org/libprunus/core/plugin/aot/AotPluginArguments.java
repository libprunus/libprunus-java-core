package org.libprunus.core.plugin.aot;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record AotPluginArguments(
        boolean enabled, List<String> basePackages, List<String> excludePackages, AotLogArguments log)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public AotPluginArguments {
        basePackages = List.copyOf(basePackages);
        excludePackages = List.copyOf(excludePackages);
    }
}
