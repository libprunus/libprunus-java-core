package org.libprunus.core.plugin;

import net.bytebuddy.build.gradle.AbstractByteBuddyTask;
import net.bytebuddy.build.gradle.ByteBuddyTaskExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.libprunus.core.plugin.aot.AotByteBuddyDispatcher;
import org.libprunus.core.plugin.aot.AotExtension;
import org.libprunus.core.plugin.aot.GenerateAotConfigTask;
import org.libprunus.core.plugin.aot.log.AotLogExtension;
import org.libprunus.core.plugin.buildlogic.JavaBuildLogic;

public final class LibprunusCorePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        applyBuildLogic(project);

        ExtensionContainer extensions = project.getExtensions();
        CoreExtension core = extensions.create("core", CoreExtension.class);
        AotExtension aot = core.getAot();
        configureByteBuddy(project, aot);
    }

    private void applyBuildLogic(Project project) {
        VersionCatalog libs = null;
        var catalogs = project.getExtensions().findByType(VersionCatalogsExtension.class);
        if (catalogs != null) {
            libs = catalogs.find("libs").orElse(null);
        }
        new JavaBuildLogic(project, libs).apply();
    }

    private void configureByteBuddy(Project project, AotExtension aot) {
        Provider<RegularFile> configFileProvider =
                project.getLayout().getBuildDirectory().file("tmp/libprunus/aot/aot-config.properties");
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        TaskProvider<GenerateAotConfigTask> generateAotConfig = project.getTasks()
                .register("generateAotConfig", GenerateAotConfigTask.class, task -> {
                    AotLogExtension aotLog = aot.getLog();
                    task.getAotEnabled().set(aot.getEnabled());
                    task.getBasePackages().set(aot.getBasePackages());
                    task.getExcludePackages().set(aot.getExcludePackages());
                    task.getLogEnabled().set(aotLog.getEnabled());
                    task.getTargetClassSuffixes().set(aotLog.getTargetClassSuffixes());
                    task.getPojoSuffixes().set(aotLog.getPojoSuffixes());
                    task.getClassNameFormat().set(aotLog.getClassNameFormat().map(value -> value.name()));
                    task.getEnterLogLevel().set(aotLog.getEnterLogLevel().map(value -> value.name()));
                    task.getExitLogLevel().set(aotLog.getExitLogLevel().map(value -> value.name()));
                    task.getHandleInaccessibleField().set(aotLog.getHandleInaccessibleField());
                    task.getMaxToStringDepth().set(aotLog.getMaxToStringDepth());
                    task.getToStringWhitelist().set(aotLog.getToStringWhitelist());
                    task.getGeneratedSourceDir()
                            .set(project.getLayout().getBuildDirectory().dir("generated/sources/aot/java/main"));
                    task.getOutputFile().set(configFileProvider);
                });

        mainSourceSet.getJava().srcDir(generateAotConfig.flatMap(GenerateAotConfigTask::getGeneratedSourceDir));
        project.getTasks()
                .named(mainSourceSet.getCompileJavaTaskName())
                .configure(task -> task.dependsOn(generateAotConfig));

        project.getPluginManager().apply("net.bytebuddy.byte-buddy-gradle-plugin");
        project.getExtensions().configure(ByteBuddyTaskExtension.class, byteBuddy -> {
            byteBuddy.transformation(transformation -> {
                transformation.setPlugin(AotByteBuddyDispatcher.class);
                transformation.getArguments().clear();
                transformation.argument(argument ->
                        argument.setValue(configFileProvider.get().getAsFile().getAbsolutePath()));
            });
        });

        project.getTasks().withType(AbstractByteBuddyTask.class).configureEach(task -> {
            task.onlyIf(t -> aot.getEnabled().getOrElse(true));
            task.dependsOn(generateAotConfig);
        });
    }
}
