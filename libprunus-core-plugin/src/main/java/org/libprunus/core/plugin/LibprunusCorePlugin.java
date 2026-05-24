package org.libprunus.core.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.libprunus.core.plugin.aot.AotConfigurer;
import org.libprunus.core.plugin.buildlogic.JavaBuildLogic;

public final class LibprunusCorePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        PrunusExtension prunus = project.getExtensions().create("prunus", PrunusExtension.class);
        new JavaBuildLogic(project, prunus.getJavaBuild()).apply();
        new AotConfigurer(project, prunus.getAot(), prunus.getJavaBuild()).apply();
    }
}
