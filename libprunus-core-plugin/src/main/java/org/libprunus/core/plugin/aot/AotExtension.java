package org.libprunus.core.plugin.aot;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.libprunus.core.plugin.aot.log.AotLogExtension;

public abstract class AotExtension {

    private final AotLogExtension log;

    @Inject
    public AotExtension(ObjectFactory objectFactory) {
        getEnabled().convention(true);
        getBasePackages().convention(List.of("org.libprunus"));
        getExcludePackages().convention(List.of());
        this.log = objectFactory.newInstance(AotLogExtension.class);
    }

    public abstract Property<Boolean> getEnabled();

    public abstract ListProperty<String> getBasePackages();

    public abstract ListProperty<String> getExcludePackages();

    public AotLogExtension getLog() {
        return log;
    }

    public void log(Action<? super AotLogExtension> action) {
        action.execute(log);
    }
}
