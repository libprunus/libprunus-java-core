package org.libprunus.core.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.libprunus.core.plugin.buildlogic.JavaBuildLogic;

public final class LibprunusCorePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        VersionCatalog libs = null;
        var catalogs = project.getExtensions().findByType(VersionCatalogsExtension.class);
        if (catalogs != null) {
            libs = catalogs.find("libs").orElse(null);
        }

        new JavaBuildLogic(project, libs).apply();
    }
}
