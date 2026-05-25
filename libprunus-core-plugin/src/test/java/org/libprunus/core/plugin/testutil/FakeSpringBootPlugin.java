package org.libprunus.core.plugin.testutil;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;

public class FakeSpringBootPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().register("bootJar", Jar.class);
        project.getTasks().register("bootRun");
    }
}
