package org.libprunus.core.plugin.aot.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.bytebuddy.dynamic.ClassFileLocator;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.work.DisableCachingByDefault;
import org.libprunus.core.plugin.aot.AotClassFileLocatorFactory;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;

@DisableCachingByDefault(because = "Abstract base task; concrete subclasses declare cacheability")
public abstract class AbstractAotActionTask extends DefaultTask {

    private final Path projectDirPath = getProject().getProjectDir().toPath();

    @FunctionalInterface
    interface AotContextAction {
        void execute(String registryClassName, ClassFileLocator locator) throws IOException;
    }

    @Input
    public String getGeneratorVersion() {
        return PrunusPluginConstants.AOT_GENERATOR_VERSION;
    }

    @Input
    public abstract Property<String> getRegistryClass();

    @Input
    public abstract Property<String> getTargetCompatibility();

    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getMainClassesDirs();

    protected final boolean executeWithAotContext(Iterable<File> runtimeClasspath, AotContextAction action)
            throws IOException {
        List<File> classesDirs = PortablePathOrder.sortByProjectRelativePath(
                getMainClassesDirs().getFiles().stream().filter(File::exists).toList(), projectDirPath);
        if (classesDirs.isEmpty()) {
            return false;
        }
        try (ClassFileLocator classFileLocator = AotClassFileLocatorFactory.create(
                classesDirs, runtimeClasspath, getTargetCompatibility().get())) {
            action.execute(getRegistryClass().get(), classFileLocator);
        }
        return true;
    }
}
