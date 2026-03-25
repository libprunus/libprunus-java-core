package org.libprunus.core.config;

public final class ConfigurationRepository {

    private volatile CoreRuntimeConfig currentSnapshot;

    public ConfigurationRepository(CoreRuntimeConfig initialConfig) {
        this.currentSnapshot = initialConfig;
    }

    public CoreRuntimeConfig getGlobalSnapshot() {
        return currentSnapshot;
    }

    public void refresh(CoreRuntimeConfig newConfig) {
        currentSnapshot = newConfig;
    }
}
