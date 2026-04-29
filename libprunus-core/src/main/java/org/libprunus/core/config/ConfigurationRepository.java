package org.libprunus.core.config;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.libprunus.core.log.runtime.AotLogRuntime;

public final class ConfigurationRepository {

    private final AtomicReference<CoreRuntimeConfig> currentSnapshot;

    public ConfigurationRepository(CoreRuntimeConfig initialConfig) {
        this.currentSnapshot =
                new AtomicReference<>(Objects.requireNonNull(initialConfig, "initialConfig must not be null"));
        AotLogRuntime.linkToDataPlane(this.currentSnapshot);
    }

    public CoreRuntimeConfig getGlobalSnapshot() {
        return currentSnapshot.get();
    }

    public void refresh(CoreRuntimeConfig newConfig) {
        CoreRuntimeConfig config = Objects.requireNonNull(newConfig, "newConfig must not be null");
        currentSnapshot.set(config);
    }
}
