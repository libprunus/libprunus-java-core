package org.libprunus.core.plugin.aot.log;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import org.libprunus.core.plugin.aot.AotClassFileLocatorFactory;

public final class FinalRegistryAggregator {

    public record AggregatedRegistryResult(int maxMessageLength, List<String> mergedWhitelist) {

        public AggregatedRegistryResult {
            mergedWhitelist = List.copyOf(mergedWhitelist);
        }
    }

    public AggregatedRegistryResult aggregate(
            String registryClassName, ClassFileLocator classFileLocator, Iterable<File> runtimeClasspath)
            throws IOException {
        ClassFileLocator parserLocator = AotClassFileLocatorFactory.compose(classFileLocator, runtimeClasspath);
        try {
            TypePool typePool = new TypePool.Default(
                    new TypePool.CacheProvider.Simple(), parserLocator, TypePool.Default.ReaderMode.EXTENDED);
            RegistryRouteGraph graph =
                    new RegistryRouteGraphBuilder().build(registryClassName, parserLocator, typePool);
            return aggregate(
                    graph.globalMaxMessageLength(),
                    graph.directToStringWhitelist(),
                    runtimeClasspath,
                    classFileLocator);
        } finally {
            parserLocator.close();
        }
    }

    private AggregatedRegistryResult aggregate(
            int maxMessageLength,
            List<String> hostWhitelist,
            Iterable<File> runtimeClasspath,
            ClassFileLocator classFileLocator) {
        int estimatedSize = RuntimeBindingAbi.CORE_BUILTIN_WHITELIST.size() + hostWhitelist.size();
        if (runtimeClasspath instanceof java.util.Collection<?> col) {
            estimatedSize += col.size() * 32;
        } else {
            estimatedSize += 64;
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>((int) (estimatedSize / 0.75f) + 1);
        merged.addAll(RuntimeBindingAbi.CORE_BUILTIN_WHITELIST);
        merged.addAll(hostWhitelist);

        for (File entry : runtimeClasspath) {
            WhitelistResourceReader.readFrom(entry, merged);
        }

        WhitelistClassNameValidator.validate(merged, classFileLocator);
        return new AggregatedRegistryResult(
                maxMessageLength, merged.stream().sorted().toList());
    }
}
