package org.libprunus.core.config;

import java.util.Objects;
import org.libprunus.core.log.runtime.AotLogRuntime;

public final class ConfigurationRepository {

    private volatile CoreRuntimeConfig currentSnapshot;

    public ConfigurationRepository(CoreRuntimeConfig initialConfig) {
        this.currentSnapshot = Objects.requireNonNull(initialConfig, "initialConfig must not be null");
        syncToDataPlane(this.currentSnapshot);
    }

    public CoreRuntimeConfig getGlobalSnapshot() {
        return currentSnapshot;
    }

    public synchronized void refresh(CoreRuntimeConfig newConfig) {
        CoreRuntimeConfig config = Objects.requireNonNull(newConfig, "newConfig must not be null");
        currentSnapshot = config;
        syncToDataPlane(config);
    }

    private void syncToDataPlane(CoreRuntimeConfig config) {
        AotLogRuntime.updateConfig(config.log());
    }
}
