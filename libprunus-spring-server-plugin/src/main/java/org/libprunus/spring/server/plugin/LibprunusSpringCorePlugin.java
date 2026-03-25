package org.libprunus.spring.server.plugin;

import io.spring.gradle.dependencymanagement.DependencyManagementPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.libprunus.core.plugin.LibprunusCorePlugin;
import org.springframework.boot.gradle.plugin.SpringBootPlugin;

public final class LibprunusSpringCorePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        var pluginManager = project.getPluginManager();
        pluginManager.apply(LibprunusCorePlugin.class);
        pluginManager.apply(SpringBootPlugin.class);
        pluginManager.apply(DependencyManagementPlugin.class);

        project.getDependencies().add("implementation", "org.libprunus:libprunus-spring-server");
    }
}
