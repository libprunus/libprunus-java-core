package org.libprunus.core.plugin.aot.task;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;
import org.libprunus.core.plugin.aot.log.RegistryMetadataWriter;

@CacheableTask
public abstract class GenerateLibraryWhitelistTask extends AbstractAotActionTask {

    @Internal
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    @InputFiles
    @Classpath
    public List<File> getRuntimeClasspathCacheInputs() {
        return RuntimeClasspathInputSelector.selectGenerateCacheInputs(
                        getRuntimeClasspath().getFiles(), false)
                .stream()
                .filter(GenerateLibraryWhitelistTask::contributesToWhitelist)
                .toList();
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() {
        try {
            boolean executed = executeWithAotContext(getRuntimeClasspath().getFiles(), (registryClassName, locator) -> {
                new RegistryMetadataWriter()
                        .write(
                                registryClassName,
                                locator,
                                getOutputDirectory().get().getAsFile().toPath());
            });
            if (!executed) {
                getLogger().info("No main classes found; library whitelist generation skipped.");
            }
        } catch (IOException | IllegalStateException exception) {
            throw new GradleException("Failed to generate library whitelist", exception);
        }
    }

    private static boolean contributesToWhitelist(File entry) {
        if (entry.isDirectory()) {
            return true;
        }
        if (!entry.isFile() || !entry.getName().endsWith(".jar")) {
            return false;
        }
        try (ZipFile zip = new ZipFile(entry)) {
            return zip.getEntry(PrunusPluginConstants.WHITELIST_RESOURCE_PATH) != null;
        } catch (IOException _) {
            return false;
        }
    }
}
