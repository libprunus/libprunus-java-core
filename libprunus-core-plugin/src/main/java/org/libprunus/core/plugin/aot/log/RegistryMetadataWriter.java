package org.libprunus.core.plugin.aot.log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;
import org.libprunus.core.plugin.aot.util.AtomicFileWriter;

public final class RegistryMetadataWriter {

    public void write(String registryClassName, ClassFileLocator classFileLocator, Path outputDir) {
        TypePool typePool = new TypePool.Default(
                new TypePool.CacheProvider.Simple(), classFileLocator, TypePool.Default.ReaderMode.EXTENDED);
        RegistryRouteGraph graph = new RegistryRouteGraphBuilder().build(registryClassName, classFileLocator, typePool);
        writeWhitelist(graph.directToStringWhitelist(), outputDir);
    }

    private void writeWhitelist(List<String> whitelist, Path outputDir) {
        Path target = outputDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH);
        try {
            StringBuilder sb = new StringBuilder(whitelist.size() * 48 + 16);
            for (String className : whitelist) {
                sb.append(className).append('\n');
            }
            byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
            AtomicFileWriter.writeIfChanged(target, content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write whitelist file: " + target, e);
        }
    }
}
