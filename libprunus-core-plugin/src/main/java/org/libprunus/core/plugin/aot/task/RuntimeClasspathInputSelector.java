package org.libprunus.core.plugin.aot.task;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipFile;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;

final class RuntimeClasspathInputSelector {

    private RuntimeClasspathInputSelector() {
        throw new UnsupportedOperationException();
    }

    public static List<File> selectGenerateCacheInputs(Set<File> runtimeClasspathEntries, boolean explicitBinding) {
        Objects.requireNonNull(runtimeClasspathEntries, "runtimeClasspathEntries");
        if (explicitBinding) {
            return List.of();
        }
        return runtimeClasspathEntries.stream()
                .filter(RuntimeClasspathInputSelector::isGenerateRelevantClasspathRoot)
                .map(RuntimeClasspathInputSelector::normalizedAbsoluteFile)
                .sorted(Comparator.comparing(File::getAbsolutePath))
                .toList();
    }

    public static List<File> selectResolveCacheInputs(Set<File> runtimeClasspathEntries, String selectedBindingClass) {
        Objects.requireNonNull(runtimeClasspathEntries, "runtimeClasspathEntries");
        Objects.requireNonNull(selectedBindingClass, "selectedBindingClass");
        String bindingClassPath = selectedBindingClass.replace('.', '/') + ".class";
        return runtimeClasspathEntries.stream()
                .filter(entry -> isResolveRelevantClasspathRootForCacheInput(entry, bindingClassPath))
                .map(RuntimeClasspathInputSelector::normalizedAbsoluteFile)
                .sorted(Comparator.comparing(File::getAbsolutePath))
                .toList();
    }

    public static Set<File> selectResolveScanEntries(Set<File> runtimeClasspathEntries, String selectedBindingClass) {
        Objects.requireNonNull(runtimeClasspathEntries, "runtimeClasspathEntries");
        Objects.requireNonNull(selectedBindingClass, "selectedBindingClass");
        String bindingClassPath = selectedBindingClass.replace('.', '/') + ".class";
        LinkedHashSet<File> selected = new LinkedHashSet<>();
        for (File entry : runtimeClasspathEntries) {
            if (isResolveRelevantClasspathRoot(entry, bindingClassPath)) {
                selected.add(entry);
            }
        }
        return selected;
    }

    private static boolean isGenerateRelevantClasspathRoot(File entry) {
        if (!entry.exists()) {
            return false;
        }
        if (entry.isDirectory()) {
            Path whitelist = entry.toPath().resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH);
            return Files.exists(whitelist);
        }
        return entry.isFile() && entry.getName().endsWith(".jar");
    }

    private static boolean isResolveRelevantClasspathRootForCacheInput(File entry, String bindingClassPath) {
        if (!entry.exists()) {
            return false;
        }
        if (entry.isDirectory()) {
            return directoryMatches(entry, bindingClassPath);
        }
        return entry.isFile() && entry.getName().endsWith(".jar");
    }

    private static boolean isResolveRelevantClasspathRoot(File entry, String bindingClassPath) {
        if (!entry.exists()) {
            return false;
        }
        if (entry.isDirectory()) {
            return directoryMatches(entry, bindingClassPath);
        }
        if (!entry.isFile() || !entry.getName().endsWith(".jar")) {
            return false;
        }
        return jarContains(entry, bindingClassPath)
                || jarContains(
                        entry,
                        PrunusPluginConstants.SPI_SERVICES_DIR + "/" + PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN);
    }

    private static boolean directoryMatches(File entry, String bindingClassPath) {
        Path root = entry.toPath();
        if (Files.exists(root.resolve(bindingClassPath))) {
            return true;
        }
        return Files.exists(root.resolve(PrunusPluginConstants.SPI_SERVICES_DIR)
                .resolve(PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN));
    }

    private static boolean jarContains(File jarFile, String exactEntryPath) {
        try (ZipFile zip = new ZipFile(jarFile)) {
            return zip.getEntry(exactEntryPath) != null;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to inspect JAR entry while filtering runtime classpath: " + jarFile.getAbsolutePath(), e);
        }
    }

    private static File normalizedAbsoluteFile(File file) {
        return file.toPath().normalize().toAbsolutePath().toFile();
    }
}
