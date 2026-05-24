package org.libprunus.core.plugin.aot.task;

import java.io.File;
import java.util.List;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;

@DisableCachingByDefault(because = "Streams provider conflict diagnostics only")
public abstract class ResolveLogConfigProviderConflictTask extends DefaultTask {

    @Internal
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    @InputFiles
    @Classpath
    public List<File> getRuntimeClasspathCacheInputs() {
        String defaultBinding =
                BindingClassSelector.defaultBindingClassName(getBindingId().get());
        String explicitBinding =
                PrunusStringUtils.normalize(getExplicitBindingClass().getOrNull());
        BindingClassSelector.SelectionResult selected = BindingClassSelector.select(explicitBinding, defaultBinding);
        return RuntimeClasspathInputSelector.selectResolveCacheInputs(
                getRuntimeClasspath().getFiles(), selected.bindingClassName());
    }

    @Input
    public abstract Property<String> getBindingId();

    @Input
    @Optional
    public abstract Property<String> getExplicitBindingClass();

    @TaskAction
    public void resolve() {
        String defaultBinding =
                BindingClassSelector.defaultBindingClassName(getBindingId().get());
        String explicitBinding =
                PrunusStringUtils.normalize(getExplicitBindingClass().getOrNull());
        BindingClassSelector.SelectionResult selected = BindingClassSelector.select(explicitBinding, defaultBinding);

        Set<File> classpathEntries = RuntimeClasspathInputSelector.selectResolveScanEntries(
                getRuntimeClasspath().getFiles(), selected.bindingClassName());
        LogConfigProviderScanner.ScannerResult scanResult = LogConfigProviderScanner.scan(
                classpathEntries,
                new LogConfigProviderScanner.ScanRequest(
                        PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN, selected.bindingClassName()));

        List<String> providerSources = scanResult.providerSources();
        BindingClassConflictChecker.checkSpiDescriptorUniqueness(providerSources)
                .ifPresent(msg -> getLogger().warn("{}", msg));

        List<String> bindingClassSources = scanResult.classSources();

        if (selected.explicit()) {
            BindingClassConflictChecker.checkBindingClassPresent(
                            selected.bindingClassName(), !bindingClassSources.isEmpty())
                    .ifPresent(msg -> {
                        throw new GradleException(msg);
                    });
            getLogger().warn("Binding class is explicitly overridden by property: {}", selected.bindingClassName());
        }

        BindingClassConflictChecker.checkBindingClassUniqueness(selected.bindingClassName(), bindingClassSources)
                .ifPresent(msg -> {
                    throw new GradleException(msg);
                });

        getLogger()
                .lifecycle(
                        "Provider conflict check: selectedBindingClass={}, explicitOverride={}, classpathEntries={} ",
                        selected.bindingClassName(),
                        selected.explicit(),
                        classpathEntries.size());
    }
}
