package org.libprunus.core.config;

import java.util.Objects;
import org.libprunus.core.log.runtime.LogRuntimeConfig;

public record CoreRuntimeConfig(LogRuntimeConfig log) {

    public CoreRuntimeConfig {
        Objects.requireNonNull(log, "log must not be null");
    }
}
