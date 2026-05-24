package org.libprunus.core.plugin.aot.task;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.libprunus.core.log.runtime.CallsiteBindingProtocol;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;
import org.libprunus.core.plugin.aot.log.BindingClassGenerator;
import org.libprunus.core.plugin.aot.log.FinalRegistryAggregator;
import org.libprunus.core.plugin.aot.log.RuntimeBindingCallsiteGenerator;
import org.libprunus.core.plugin.aot.util.AtomicFileWriter;

@CacheableTask
public abstract class GenerateAotBindingTask extends AbstractAotActionTask {

    @Internal
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    @InputFiles
    @Classpath
    public List<File> getRuntimeClasspathCacheInputs() {
        return RuntimeClasspathInputSelector.selectGenerateCacheInputs(
                getRuntimeClasspath().getFiles(),
                PrunusStringUtils.normalize(getExplicitBindingClass().getOrNull()) != null);
    }

    @Input
    public abstract Property<String> getBindingId();

    @Input
    @Optional
    public abstract Property<String> getExplicitBindingClass();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        String bindingId = getBindingId().get();
        String defaultBinding = BindingClassSelector.defaultBindingClassName(bindingId);
        String explicit = PrunusStringUtils.normalize(getExplicitBindingClass().getOrNull());
        BindingClassSelector.SelectionResult selected = BindingClassSelector.select(explicit, defaultBinding);

        Path outputDir = getOutputDirectory().get().getAsFile().toPath();

        if (!selected.explicit()) {
            boolean generated =
                    executeWithAotContext(getRuntimeClasspath().getFiles(), (registryClassName, locator) -> {
                        FinalRegistryAggregator.AggregatedRegistryResult result = new FinalRegistryAggregator()
                                .aggregate(
                                        registryClassName,
                                        locator,
                                        getRuntimeClasspath().getFiles());
                        new BindingClassGenerator()
                                .generate(
                                        selected.bindingClassName(),
                                        outputDir,
                                        getTargetCompatibility().get(),
                                        result.maxMessageLength(),
                                        result.mergedWhitelist());
                    });
            if (!generated) {
                return;
            }
        }

        new RuntimeBindingCallsiteGenerator()
                .generate(
                        bindingId,
                        selected.bindingClassName(),
                        outputDir,
                        getTargetCompatibility().get());

        String callsiteClassName = RuntimeBindingCallsiteGenerator.callsiteClassName(bindingId);
        writeAotResourceFiles(outputDir, selected.bindingClassName(), callsiteClassName);
    }

    private static void writeAotResourceFiles(Path outputDir, String bindingClassName, String callsiteClassName)
            throws IOException {
        Path spiDir = outputDir.resolve(PrunusPluginConstants.SPI_SERVICES_DIR);
        AtomicFileWriter.writeIfChanged(
                spiDir.resolve(PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN),
                bindingClassName + "\n",
                StandardCharsets.UTF_8);

        Path callsiteDir = outputDir.resolve(PrunusPluginConstants.AOT_RUNTIME_CALLSITE_DIR);
        AtomicFileWriter.writeIfChanged(
                callsiteDir.resolve(CallsiteBindingProtocol.RESOURCE_FILENAME),
                callsiteClassName + "\n",
                StandardCharsets.UTF_8);
    }
}
