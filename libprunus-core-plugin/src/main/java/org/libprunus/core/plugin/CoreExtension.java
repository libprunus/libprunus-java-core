package org.libprunus.core.plugin;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.libprunus.core.plugin.aot.AotExtension;

public abstract class CoreExtension {

    private final AotExtension aot;

    @Inject
    public CoreExtension(ObjectFactory objectFactory) {
        this.aot = objectFactory.newInstance(AotExtension.class);
    }

    AotExtension getAot() {
        return aot;
    }

    public void aot(Action<? super AotExtension> action) {
        action.execute(aot);
    }
}
