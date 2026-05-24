package org.libprunus.core.plugin.aot;

import javax.inject.Inject;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

public abstract class AotExtension {

    @Inject
    public AotExtension() {
        getEnabled().convention(false);
        getMode().convention(AotMode.APPLICATION);
    }

    public abstract Property<Boolean> getEnabled();

    public abstract Property<AotMode> getMode();

    public abstract Property<String> getLogRegistryClass();

    public Provider<Boolean> getEnabledInApplicationMode() {
        return getEnabled().zip(getMode(), (e, m) -> e && m == AotMode.APPLICATION);
    }

    public Provider<Boolean> getEnabledInLibraryMode() {
        return getEnabled().zip(getMode(), (e, m) -> e && m == AotMode.LIBRARY);
    }
}
