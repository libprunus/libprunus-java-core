package org.libprunus.core.plugin.aot;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class GenerateAotConfigTask extends DefaultTask {

    @Input
    public abstract Property<Boolean> getAotEnabled();

    @Input
    public abstract ListProperty<String> getBasePackages();

    @Input
    public abstract ListProperty<String> getExcludePackages();

    @Input
    public abstract Property<Boolean> getLogEnabled();

    @Input
    public abstract ListProperty<String> getTargetClassSuffixes();

    @Input
    public abstract ListProperty<String> getPojoSuffixes();

    @Input
    public abstract Property<String> getClassNameFormat();

    @Input
    public abstract Property<String> getEnterLogLevel();

    @Input
    public abstract Property<String> getExitLogLevel();

    @Input
    public abstract Property<Boolean> getHandleInaccessibleField();

    @Input
    public abstract Property<Integer> getMaxToStringDepth();

    @Input
    public abstract ListProperty<String> getToStringWhitelist();

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedSourceDir();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    void generate() {
        AotPluginArguments arguments = new AotPluginArguments(
                getAotEnabled().get(),
                getBasePackages().get(),
                getExcludePackages().get(),
                new AotLogArguments(
                        getLogEnabled().get(),
                        getTargetClassSuffixes().get(),
                        getPojoSuffixes().get(),
                        getClassNameFormat().get(),
                        getEnterLogLevel().get(),
                        getExitLogLevel().get(),
                        getHandleInaccessibleField().get(),
                        getMaxToStringDepth().get(),
                        getToStringWhitelist().get()));
        AotPluginArgumentsFile.write(getOutputFile().get().getAsFile().toPath(), arguments);
        AotToStringWhitelistGenerator.generateSource(
                getGeneratedSourceDir().get().getAsFile().toPath(),
                arguments.log().toStringWhitelist());
    }
}
