package org.libprunus.core.plugin.aot;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import org.libprunus.core.plugin.aot.log.AotLogByteBuddyPlugin;

public final class AotByteBuddyDispatcher implements Plugin {

    record RegisteredPlugin(AotDispatcherPluginSlot slot, Plugin plugin) {}

    record DispatcherInputs(String registryClass, List<File> classesOutputDir, List<File> classpath) {}

    private final AotCompileContext context;
    private final List<RegisteredPlugin> plugins;
    private final ClassFileLocator pluginClassFileLocator;
    private volatile boolean closed = false;

    public AotByteBuddyDispatcher(String registryClass, File rootLocation, File[] classpath) {
        this(new DispatcherInputs(registryClass, toRootList(rootLocation), sanitizeClasspath(rootLocation, classpath)));
    }

    AotByteBuddyDispatcher(DispatcherInputs inputs) {
        this.context = new AotCompileContext();
        this.pluginClassFileLocator = buildClassFileLocator(inputs.classpath(), inputs.classesOutputDir());
        List<RegisteredPlugin> initializedPlugins;
        try {
            initializedPlugins = initPlugins(inputs, this.context, this.pluginClassFileLocator);
        } catch (Throwable t) {
            try {
                this.pluginClassFileLocator.close();
            } catch (IOException e) {
                t.addSuppressed(e);
            }
            throw t;
        }
        this.plugins = initializedPlugins;
    }

    private static List<File> toRootList(File rootLocation) {
        if (rootLocation == null) {
            return List.of();
        }
        return List.of(rootLocation);
    }

    private static List<File> sanitizeClasspath(File rootLocation, File[] classpath) {
        if (classpath == null || classpath.length == 0) {
            return List.of();
        }
        File absoluteRoot = rootLocation != null ? rootLocation.getAbsoluteFile() : null;
        Set<File> result = new LinkedHashSet<>();
        for (File file : classpath) {
            if (file != null) {
                File absoluteFile = file.getAbsoluteFile();
                if (!absoluteFile.equals(absoluteRoot)) {
                    result.add(absoluteFile);
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public boolean matches(TypeDescription target) {
        if (closed) {
            return false;
        }
        return getOrComputePluginMatchMask(target) != 0;
    }

    @Override
    public DynamicType.Builder<?> apply(
            DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassFileLocator classFileLocator) {
        if (closed) {
            return builder;
        }
        int cached = context.peekMask(typeDescription.getName());
        int mask = AotCompileContext.isMissingMask(cached) ? getOrComputePluginMatchMask(typeDescription) : cached;
        if (mask == 0) {
            return builder;
        }
        DynamicType.Builder<?> currentBuilder = builder;
        for (RegisteredPlugin registeredPlugin : plugins) {
            if ((mask & registeredPlugin.slot().bitMask()) != 0) {
                currentBuilder = registeredPlugin.plugin().apply(currentBuilder, typeDescription, classFileLocator);
            }
        }
        return currentBuilder;
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        performCleanupAndThrowIfFailed();
    }

    private void performCleanupAndThrowIfFailed() throws IOException {
        IoExceptionAggregator agg = new IoExceptionAggregator();
        for (RegisteredPlugin registeredPlugin : plugins) {
            agg.tryClose(registeredPlugin.plugin());
        }
        try {
            context.clear();
        } finally {
            agg.tryClose(pluginClassFileLocator);
        }
        agg.throwIfNotEmpty();
    }

    private static final class IoExceptionAggregator {
        private IOException aggregated;

        void tryClose(Closeable resource) {
            try {
                resource.close();
            } catch (IOException e) {
                add(e);
            }
        }

        private void add(IOException e) {
            if (aggregated == null) {
                aggregated = e;
            } else {
                aggregated.addSuppressed(e);
            }
        }

        void throwIfNotEmpty() throws IOException {
            if (aggregated != null) {
                throw aggregated;
            }
        }
    }

    private int getOrComputePluginMatchMask(TypeDescription target) {
        return context.computeMaskIfAbsent(target.getName(), key -> computeMask(target, plugins));
    }

    private static int computeMask(TypeDescription target, List<RegisteredPlugin> plugins) {
        int computedMask = 0;
        for (RegisteredPlugin registeredPlugin : plugins) {
            if (registeredPlugin.plugin().matches(target)) {
                computedMask |= registeredPlugin.slot().bitMask();
            }
        }
        return computedMask;
    }

    private static List<RegisteredPlugin> initPlugins(
            DispatcherInputs inputs, AotCompileContext context, ClassFileLocator classFileLocator) {
        if (inputs.registryClass() == null || inputs.registryClass().isBlank()) {
            return List.of();
        }
        ClassFileLocator parserLocator =
                new ClassFileLocator.Compound(classFileLocator, AotClassFileLocatorFactory.BOOT_LOADER_LOCATOR);
        return List.of(new RegisteredPlugin(
                AotDispatcherPluginSlot.LOG,
                new AotLogByteBuddyPlugin(inputs.registryClass(), parserLocator, context)));
    }

    private static ClassFileLocator buildClassFileLocator(List<File> classpath, List<File> classesOutputDir) {
        List<ClassFileLocator> locators = new ArrayList<>();
        try {
            AotClassFileLocatorFactory.appendFileLocators(classesOutputDir, locators);
            AotClassFileLocatorFactory.appendFileLocators(classpath, locators);
            if (locators.isEmpty()) {
                return ClassFileLocator.NoOp.INSTANCE;
            }
            return new ClassFileLocator.Compound(locators);
        } catch (Throwable t) {
            for (ClassFileLocator locator : locators) {
                try {
                    locator.close();
                } catch (IOException e) {
                    t.addSuppressed(e);
                }
            }
            throw t;
        }
    }
}
