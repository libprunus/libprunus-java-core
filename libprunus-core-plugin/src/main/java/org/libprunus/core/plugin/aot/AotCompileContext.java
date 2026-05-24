package org.libprunus.core.plugin.aot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

public final class AotCompileContext {

    private static final int NO_CACHED_MASK = -1;

    private final Map<String, Integer> matchedPluginMasks = new ConcurrentHashMap<>();
    private final Map<ClassFileLocator, TypePool> typePoolsByLocator = new ConcurrentHashMap<>();

    public int computeMaskIfAbsent(String className, ToIntFunction<String> loader) {
        return matchedPluginMasks.computeIfAbsent(className, loader::applyAsInt);
    }

    public int peekMask(String className) {
        Integer cached = matchedPluginMasks.get(className);
        return cached == null ? NO_CACHED_MASK : cached;
    }

    public static boolean isMissingMask(int mask) {
        return mask == NO_CACHED_MASK;
    }

    public TypePool sharedTypePool(ClassFileLocator locator) {
        return typePoolsByLocator.computeIfAbsent(locator, TypePool.Default::of);
    }

    public void clear() {
        matchedPluginMasks.clear();
        typePoolsByLocator.clear();
    }
}
