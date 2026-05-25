package org.libprunus.core.plugin.aot;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import net.bytebuddy.build.gradle.AbstractByteBuddyTask;
import net.bytebuddy.build.gradle.AbstractByteBuddyTaskExtension;
import net.bytebuddy.build.gradle.Adjustment;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.libprunus.core.plugin.aot.task.AbstractAotActionTask;
import org.libprunus.core.plugin.aot.task.GenerateAotBindingTask;
import org.libprunus.core.plugin.aot.task.GenerateLibraryWhitelistTask;
import org.libprunus.core.plugin.aot.task.ResolveLogConfigProviderConflictTask;
import org.libprunus.core.plugin.aot.task.VerifyPackagedProviderBindingTask;
import org.libprunus.core.plugin.buildlogic.JavaBuildExtension;

public final class AotConfigurer {

    private static final String MAIN_BYTE_BUDDY_TASK_NAME = "byteBuddy";
    private static final String ONLY_IF_APPLICATION_MODE = "AOT enabled in application mode";
    private static final String ONLY_IF_AOT_ENABLED = "AOT enabled";

    private static final List<PackagingHook> PACKAGING_HOOKS = List.of(
            new PackagingHook(
                    "org.springframework.boot", "bootJar", PrunusPluginConstants.VERIFY_BOOT_JAR_PROVIDER_BINDING_TASK),
            new PackagingHook(
                    "com.github.johnrengelman.shadow",
                    "shadowJar",
                    PrunusPluginConstants.VERIFY_SHADOW_JAR_PROVIDER_BINDING_TASK));

    private final Project project;
    private final AotExtension aot;
    private final JavaBuildExtension javaBuild;
    private final Provider<String> mainBindingIdProvider;
    private final Spec<Task> applicationModeOnlyIf;
    private final Spec<Task> libraryModeOnlyIf;
    private final Spec<Task> enabledOnlyIf;
    private final Path projectDirPath;
    private final FileCollection emptyFiles;

    public AotConfigurer(Project project, AotExtension aot, JavaBuildExtension javaBuild) {
        this.project = project;
        this.aot = aot;
        this.javaBuild = javaBuild;
        this.projectDirPath = project.getProjectDir().toPath();
        this.emptyFiles = project.files();
        this.mainBindingIdProvider = project.provider(() -> new BindingIdGenerator()
                .generate(
                        resolveCoordinate(project.getGroup()),
                        resolveCoordinate(project.getName()),
                        resolveCoordinate(project.getVersion()),
                        project.getPath(),
                        SourceSet.MAIN_SOURCE_SET_NAME));
        Provider<Boolean> enabledInApp = aot.getEnabledInApplicationMode();
        Provider<Boolean> enabledInLib = aot.getEnabledInLibraryMode();
        Provider<Boolean> enabled = aot.getEnabled();
        this.applicationModeOnlyIf = task -> enabledInApp.getOrElse(false);
        this.libraryModeOnlyIf = task -> enabledInLib.getOrElse(false);
        this.enabledOnlyIf = task -> enabled.getOrElse(false);
    }

    public void apply() {
        configureByteBuddy();

        TaskProvider<ResolveLogConfigProviderConflictTask> resolveProviderConflictTask =
                registerResolveLogConfigProviderConflictTask();
        TaskProvider<GenerateAotBindingTask> generateAotBindingTask = registerGenerateAotBindingTask();
        bindProviderConflictTaskLifecycle(resolveProviderConflictTask);
        bindPackagingTaskLifecycle(resolveProviderConflictTask, generateAotBindingTask);
        registerPackagingVerificationTasks(resolveProviderConflictTask, generateAotBindingTask);
        registerGenerateLibraryWhitelistTask();

        resolveProviderConflictTask.configure(t -> t.onlyIf(ONLY_IF_APPLICATION_MODE, applicationModeOnlyIf));
        generateAotBindingTask.configure(t -> t.onlyIf(ONLY_IF_APPLICATION_MODE, applicationModeOnlyIf));
        project.getTasks()
                .named(PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK)
                .configure(t -> t.onlyIf("AOT enabled in library mode", libraryModeOnlyIf));

        project.afterEvaluate(unused -> validateLogRegistryClassPresentWhenEnabled());
    }

    private void validateLogRegistryClassPresentWhenEnabled() {
        if (!aot.getEnabled().getOrElse(false)) {
            return;
        }
        if (aot.getLogRegistryClass().getOrElse("").isBlank()) {
            throw new IllegalStateException("prunus.aot.logRegistryClass must be set when prunus.aot.enabled is true. "
                    + "Either set logRegistryClass to a fully-qualified @LogRegistry class name, "
                    + "or leave enabled at its default (false) to disable AOT entirely.");
        }
    }

    void configureByteBuddy() {
        project.getPluginManager().apply("net.bytebuddy.byte-buddy-gradle-plugin");

        SourceSet mainSourceSet = getMainSourceSet();
        registerByteBuddyForMain(mainSourceSet);
        bindByteBuddyTaskDependencies(mainSourceSet);
    }

    private void registerByteBuddyForMain(SourceSet mainSourceSet) {
        project.getTasks()
                .named(mainSourceSet.getClassesTaskName())
                .configure(classesTask -> classesTask.dependsOn(MAIN_BYTE_BUDDY_TASK_NAME));

        Object bbExt = project.getExtensions().findByName(MAIN_BYTE_BUDDY_TASK_NAME);
        if (bbExt instanceof AbstractByteBuddyTaskExtension<?> typedExt) {
            typedExt.setAdjustment(Adjustment.NONE);
            typedExt.transformation(transformation -> transformation.setPlugin(AotByteBuddyDispatcher.class));
            project.afterEvaluate(unused -> typedExt.getTransformations().forEach(transformation -> {
                if (AotByteBuddyDispatcher.class.equals(transformation.getPlugin())) {
                    transformation.getArguments().clear();
                    String registryClass = aot.getLogRegistryClass().getOrElse("");
                    transformation.argument(arg -> {
                        arg.setIndex(0);
                        arg.setValue(registryClass);
                    });
                }
            }));
        }
    }

    private void bindByteBuddyTaskDependencies(SourceSet mainSourceSet) {
        project.getTasks()
                .withType(AbstractByteBuddyTask.class)
                .matching(task -> MAIN_BYTE_BUDDY_TASK_NAME.equals(task.getName()))
                .configureEach(task -> bindSingleByteBuddyTask(task, mainSourceSet));
    }

    private void bindSingleByteBuddyTask(AbstractByteBuddyTask task, SourceSet sourceSet) {
        FileCollection actualRuntimeClasspath = resolveRuntimeClasspath(sourceSet);
        Provider<String> classesOutputDirPath = sourceSet
                .getJava()
                .getDestinationDirectory()
                .map(dir -> portableRelativePath(projectDirPath, dir.getAsFile().toPath()));

        registerByteBuddyTaskInputs(task, aot.getLogRegistryClass(), classesOutputDirPath, actualRuntimeClasspath);
        task.onlyIf(ONLY_IF_AOT_ENABLED, enabledOnlyIf);
    }

    private FileCollection resolveRuntimeClasspath(SourceSet sourceSet) {
        FileCollection appClasspath =
                project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());
        FileCollection appClasspathWithoutProjectOutputs = appClasspath.minus(sourceSet.getOutput());
        return project.files(
                aot.getMode().map(m -> m == AotMode.APPLICATION ? appClasspathWithoutProjectOutputs : emptyFiles));
    }

    private void registerByteBuddyTaskInputs(
            AbstractByteBuddyTask task,
            Provider<String> registryClass,
            Provider<String> classesOutputDirPath,
            FileCollection actualRuntimeClasspath) {
        task.getInputs().property(PrunusPluginConstants.AOT_INPUT_REGISTRY_CLASS, registryClass);
        task.getInputs().property(PrunusPluginConstants.AOT_INPUT_CLASSES_OUTPUT_DIR, classesOutputDirPath);
        task.getInputs()
                .files(actualRuntimeClasspath)
                .withPropertyName(PrunusPluginConstants.AOT_INPUT_RUNTIME_CLASSPATH)
                .withPathSensitivity(PathSensitivity.NONE);
    }

    private static String portableRelativePath(Path projectDirPath, Path target) {
        String s = projectDirPath.relativize(target).toString();
        return File.separatorChar == '/' ? s : s.replace('\\', '/');
    }

    private <T extends AbstractAotActionTask> void bindCommonAotProperties(T task) {
        task.onlyIf(ONLY_IF_AOT_ENABLED, enabledOnlyIf);
        task.getRegistryClass().set(aot.getLogRegistryClass());
        task.getTargetCompatibility().set(javaBuild.getTargetJavaVersion().map(String::valueOf));
    }

    private void registerGenerateLibraryWhitelistTask() {
        SourceSet mainSourceSet = getMainSourceSet();
        FileCollection safeClasspath = mainSafeRuntimeClasspath(mainSourceSet);

        var generateWhitelistTask = project.getTasks()
                .register(
                        PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK,
                        GenerateLibraryWhitelistTask.class,
                        task -> {
                            bindCommonAotProperties(task);
                            task.dependsOn(MAIN_BYTE_BUDDY_TASK_NAME);
                            task.getRuntimeClasspath().from(safeClasspath);
                            task.getMainClassesDirs()
                                    .from(mainSourceSet.getOutput().getClassesDirs());
                            task.getOutputDirectory()
                                    .set(project.getLayout()
                                            .getBuildDirectory()
                                            .dir(PrunusPluginConstants.GENERATED_LIBRARY_WHITELIST_DIR));
                        });

        project.getTasks()
                .named(mainSourceSet.getJarTaskName(), AbstractArchiveTask.class)
                .configure(jar -> jar.from(generateWhitelistTask.flatMap(t -> t.getOutputDirectory())));
    }

    private TaskProvider<GenerateAotBindingTask> registerGenerateAotBindingTask() {
        SourceSet mainSourceSet = getMainSourceSet();
        FileCollection safeClasspath = mainSafeRuntimeClasspath(mainSourceSet);

        var generateAotBindingTask = project.getTasks()
                .register(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK, GenerateAotBindingTask.class, task -> {
                    bindCommonAotProperties(task);
                    task.dependsOn(MAIN_BYTE_BUDDY_TASK_NAME);
                    task.getRuntimeClasspath().from(safeClasspath);
                    task.getMainClassesDirs().from(mainSourceSet.getOutput().getClassesDirs());
                    task.getBindingId().set(mainBindingIdProvider);
                    task.getExplicitBindingClass()
                            .set(project.getProviders()
                                    .gradleProperty(PrunusPluginConstants.AOT_PROVIDER_BINDING_CLASS_PROPERTY)
                                    .orElse(""));
                    task.getOutputDirectory()
                            .set(project.getLayout()
                                    .getBuildDirectory()
                                    .dir(PrunusPluginConstants.GENERATED_AOT_BINDING_DIR));
                });

        mainSourceSet.getOutput().dir(generateAotBindingTask.flatMap(t -> t.getOutputDirectory()));
        project.getTasks()
                .named(mainSourceSet.getClassesTaskName())
                .configure(classesTask -> classesTask.dependsOn(generateAotBindingTask));
        return generateAotBindingTask;
    }

    private TaskProvider<ResolveLogConfigProviderConflictTask> registerResolveLogConfigProviderConflictTask() {
        SourceSet mainSourceSet = getMainSourceSet();
        FileCollection safeClasspath = mainSafeRuntimeClasspath(mainSourceSet);

        return project.getTasks()
                .register(
                        PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK,
                        ResolveLogConfigProviderConflictTask.class,
                        task -> {
                            task.getRuntimeClasspath().from(safeClasspath);
                            task.getBindingId().set(mainBindingIdProvider);
                            task.getExplicitBindingClass()
                                    .set(project.getProviders()
                                            .gradleProperty(PrunusPluginConstants.AOT_PROVIDER_BINDING_CLASS_PROPERTY)
                                            .orElse(""));
                            task.onlyIf(ONLY_IF_AOT_ENABLED, enabledOnlyIf);
                        });
    }

    private void bindProviderConflictTaskLifecycle(TaskProvider<ResolveLogConfigProviderConflictTask> taskProvider) {
        project.getTasks().named("check").configure(task -> task.dependsOn(taskProvider));
        project.getPluginManager().withPlugin("application", p -> project.getTasks()
                .named("run")
                .configure(task -> task.dependsOn(taskProvider)));
        project.getPluginManager().withPlugin("org.springframework.boot", p -> project.getTasks()
                .named("bootRun")
                .configure(task -> task.dependsOn(taskProvider)));
    }

    private void bindPackagingTaskLifecycle(
            TaskProvider<ResolveLogConfigProviderConflictTask> resolveTask,
            TaskProvider<GenerateAotBindingTask> generateTask) {
        bindArchiveTaskToLifecycle(getMainSourceSet().getJarTaskName(), resolveTask, generateTask);
        for (PackagingHook hook : PACKAGING_HOOKS) {
            project.getPluginManager()
                    .withPlugin(
                            hook.pluginId(),
                            p -> bindArchiveTaskToLifecycle(hook.archiveTaskName(), resolveTask, generateTask));
        }
    }

    private void bindArchiveTaskToLifecycle(
            String archiveTaskName,
            TaskProvider<ResolveLogConfigProviderConflictTask> resolveTask,
            TaskProvider<GenerateAotBindingTask> generateTask) {
        project.getTasks().named(archiveTaskName).configure(task -> {
            task.dependsOn(resolveTask);
            task.dependsOn(generateTask);
        });
    }

    private void registerPackagingVerificationTasks(
            TaskProvider<ResolveLogConfigProviderConflictTask> resolveTask,
            TaskProvider<GenerateAotBindingTask> generateTask) {
        for (PackagingHook hook : PACKAGING_HOOKS) {
            registerJarVerifyTask(hook, resolveTask, generateTask);
        }
    }

    private void registerJarVerifyTask(
            PackagingHook hook,
            TaskProvider<ResolveLogConfigProviderConflictTask> resolveTask,
            TaskProvider<GenerateAotBindingTask> generateTask) {
        project.getPluginManager().withPlugin(hook.pluginId(), appliedPlugin -> {
            TaskProvider<VerifyPackagedProviderBindingTask> verifyTask = project.getTasks()
                    .register(hook.verifyTaskName(), VerifyPackagedProviderBindingTask.class, task -> {
                        task.dependsOn(resolveTask);
                        task.dependsOn(generateTask);
                        task.getBindingId().set(mainBindingIdProvider);
                        task.getExplicitBindingClass()
                                .set(project.getProviders()
                                        .gradleProperty(PrunusPluginConstants.AOT_PROVIDER_BINDING_CLASS_PROPERTY)
                                        .orElse(""));
                        task.getArchiveFile()
                                .set(project.getTasks()
                                        .named(hook.archiveTaskName(), AbstractArchiveTask.class)
                                        .flatMap(AbstractArchiveTask::getArchiveFile));
                    });
            verifyTask.configure(t -> t.onlyIf(ONLY_IF_APPLICATION_MODE, applicationModeOnlyIf));
            project.getTasks().named("check").configure(checkTask -> checkTask.dependsOn(verifyTask));
        });
    }

    private FileCollection mainSafeRuntimeClasspath(SourceSet mainSourceSet) {
        FileCollection runtimeCp =
                project.getConfigurations().getByName(mainSourceSet.getRuntimeClasspathConfigurationName());
        return runtimeCp.minus(mainSourceSet.getOutput());
    }

    private JavaPluginExtension getJavaExtension() {
        return project.getExtensions().getByType(JavaPluginExtension.class);
    }

    private SourceSet getMainSourceSet() {
        return getJavaExtension().getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    }

    private static String resolveCoordinate(Object value) {
        if (value instanceof Provider<?> provider) {
            value = provider.getOrNull();
        }
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private record PackagingHook(String pluginId, String archiveTaskName, String verifyTaskName) {}
}
