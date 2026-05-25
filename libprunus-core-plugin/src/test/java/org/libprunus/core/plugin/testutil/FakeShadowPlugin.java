package org.libprunus.core.plugin.testutil;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;

public class FakeShadowPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().register("shadowJar", Jar.class);
    }
}
