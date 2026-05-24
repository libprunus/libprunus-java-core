package org.libprunus.core.plugin;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.libprunus.core.plugin.aot.AotExtension;
import org.libprunus.core.plugin.buildlogic.JavaBuildExtension;

public abstract class PrunusExtension {

    private final JavaBuildExtension javaBuild;
    private final AotExtension aot;

    @Inject
    public PrunusExtension(ObjectFactory objectFactory) {
        this.javaBuild = objectFactory.newInstance(JavaBuildExtension.class);
        this.aot = objectFactory.newInstance(AotExtension.class);
    }

    public JavaBuildExtension getJavaBuild() {
        return javaBuild;
    }

    public void javaBuild(Action<? super JavaBuildExtension> action) {
        action.execute(javaBuild);
    }

    public AotExtension getAot() {
        return aot;
    }

    public void aot(Action<? super AotExtension> action) {
        action.execute(aot);
    }
}
