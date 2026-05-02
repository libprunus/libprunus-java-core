package org.libprunus.core.plugin.aot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;

public final class AotByteBuddyDispatcher implements Plugin {

    static record RegisteredPlugin(AotDispatcherPluginSlot slot, Plugin plugin) {}

    private final AotCompileContext context;
    private final PackagePrefixMatcher baseMatcher;
    private final PackagePrefixMatcher excludeMatcher;
    private final List<RegisteredPlugin> plugins = new ArrayList<>();

    public AotByteBuddyDispatcher(String configFilePath) {
        AotPluginArguments effectiveArguments = loadArguments(configFilePath);
        this.context = new AotCompileContext(
                effectiveArguments.log().handleInaccessibleField(), effectiveArguments.basePackages());
        this.baseMatcher = new PackagePrefixMatcher(effectiveArguments.basePackages(), true);
        this.excludeMatcher = new PackagePrefixMatcher(effectiveArguments.excludePackages(), false);
        if (effectiveArguments.log().enabled()) {
            this.plugins.add(register(AotDispatcherPluginSlot.LOG, effectiveArguments));
        }
    }

    @Override
    public boolean matches(TypeDescription target) {
        if (!isInGlobalScope(target.getName())) {
            return false;
        }
        return getOrComputePluginMatchMask(target) != 0;
    }

    @Override
    public DynamicType.Builder<?> apply(
            DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassFileLocator classFileLocator) {
        int mask = getOrComputePluginMatchMask(typeDescription);
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
        IOException aggregated = null;
        try {
            for (RegisteredPlugin registeredPlugin : plugins) {
                try {
                    registeredPlugin.plugin().close();
                } catch (Throwable exception) {
                    if (exception instanceof Error error) {
                        throw error;
                    }
                    if (aggregated == null) {
                        aggregated = exception instanceof IOException ioEx
                                ? ioEx
                                : new IOException(exception.getMessage(), exception);
                    } else {
                        aggregated.addSuppressed(exception);
                    }
                }
            }
        } finally {
            try {
                context.clear();
            } catch (RuntimeException exception) {
                if (aggregated != null) {
                    aggregated.addSuppressed(exception);
                } else {
                    throw exception;
                }
            }
        }
        if (aggregated != null) {
            throw aggregated;
        }
    }

    private int getOrComputePluginMatchMask(TypeDescription target) {
        return context.computeMaskIfAbsent(target.getName(), name -> {
            int computedMask = 0;
            for (RegisteredPlugin registeredPlugin : plugins) {
                if (registeredPlugin.plugin().matches(target)) {
                    computedMask |= registeredPlugin.slot().bitMask();
                }
            }
            return computedMask;
        });
    }

    private boolean isInGlobalScope(String className) {
        return baseMatcher.matches(className) && !excludeMatcher.matches(className);
    }

    private RegisteredPlugin register(AotDispatcherPluginSlot slot, AotPluginArguments arguments) {
        return new RegisteredPlugin(slot, slot.createPlugin(arguments, context));
    }

    private AotPluginArguments loadArguments(String configFilePath) {
        Path resolvedConfigPath = Path.of(configFilePath).toAbsolutePath().normalize();
        try {
            return AotPluginArgumentsFile.read(resolvedConfigPath);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to load AOT config from file: " + resolvedConfigPath, exception);
        }
    }
}
