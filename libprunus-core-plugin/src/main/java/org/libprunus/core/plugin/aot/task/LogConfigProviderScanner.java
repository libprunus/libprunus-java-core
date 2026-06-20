package org.libprunus.core.plugin.aot.task;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jspecify.annotations.Nullable;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;

final class LogConfigProviderScanner {

    // WHY: build-scoped shared cache; JarCacheKey (path + mtime + size) self-invalidates on jar overwrite,
    // so daemon-long lifetime can safely reuse the index across scan calls and Tasks.
    private static final ConcurrentHashMap<JarCacheKey, JarEntryIndex> JAR_INDEX_CACHE = new ConcurrentHashMap<>();

    private LogConfigProviderScanner() {
        throw new UnsupportedOperationException();
    }

    public static ScannerResult scan(Set<File> classpathEntries, ScanRequest request) {
        Objects.requireNonNull(classpathEntries, "classpathEntries");
        Objects.requireNonNull(request, "request");

        String spiPath = request.spiServiceName() == null
                ? null
                : PrunusPluginConstants.SPI_SERVICES_DIR + "/" + request.spiServiceName();
        String classPath = request.binaryClassName() == null
                ? null
                : request.binaryClassName().replace('.', '/') + ".class";

        List<String> providerSources = new ArrayList<>();
        List<String> classSources = new ArrayList<>();

        List<File> sortedEntries = PortablePathOrder.sortByPortableTailPath(
                classpathEntries.stream().toList());

        for (File entry : sortedEntries) {
            ScanHit hit = scanEntry(entry, spiPath, classPath);
            if (hit.providerSource()) {
                providerSources.add(entry.getAbsolutePath());
            }
            if (hit.classSource()) {
                classSources.add(entry.getAbsolutePath());
            }
        }

        return new ScannerResult(providerSources, classSources);
    }

    private static ScanHit scanEntry(File entry, @Nullable String spiPath, @Nullable String classPath) {
        if (!entry.exists()) {
            return ScanHit.NONE;
        }
        if (entry.isDirectory()) {
            return scanDirectory(entry, spiPath, classPath);
        }
        if (entry.isFile() && entry.getName().endsWith(".jar")) {
            return scanJar(entry, spiPath, classPath);
        }
        return ScanHit.NONE;
    }

    private static ScanHit scanDirectory(File entry, @Nullable String spiPath, @Nullable String classPath) {
        boolean providerSource = spiPath != null && containsDirectoryResource(entry, spiPath);
        boolean classSource = classPath != null && containsDirectoryResource(entry, classPath);
        return new ScanHit(providerSource, classSource);
    }

    private static ScanHit scanJar(File entry, @Nullable String spiPath, @Nullable String classPath) {
        JarEntryIndex index = loadJarEntryIndex(entry);
        boolean providerSource = spiPath != null && index.contains(spiPath);
        boolean classSource = classPath != null && index.contains(classPath);
        return new ScanHit(providerSource, classSource);
    }

    private static JarEntryIndex loadJarEntryIndex(File jarFile) {
        JarCacheKey key = new JarCacheKey(jarFile.getAbsolutePath(), jarFile.lastModified(), jarFile.length());
        return JAR_INDEX_CACHE.computeIfAbsent(key, k -> buildJarEntryIndex(jarFile));
    }

    private static JarEntryIndex buildJarEntryIndex(File jarFile) {
        try (ZipFile zip = new ZipFile(jarFile)) {
            Set<String> entries = new HashSet<>();
            zip.stream()
                    .map(ZipEntry::getName)
                    .filter(LogConfigProviderScanner::isSafeEntryName)
                    .forEach(entries::add);
            return new JarEntryIndex(entries);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan JAR file for SPI/metadata: " + jarFile.getAbsolutePath(), e);
        }
    }

    private static boolean isSafeEntryName(String name) {
        if (name.startsWith("/") || name.startsWith("\\")) {
            return false;
        }
        for (String segment : name.split("[/\\\\]", -1)) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsDirectoryResource(File entry, String resourcePath) {
        try {
            Path rootLexicalPath = entry.toPath().normalize().toAbsolutePath();
            Path targetLexicalPath =
                    rootLexicalPath.resolve(resourcePath).normalize().toAbsolutePath();
            ensureConfined(rootLexicalPath, targetLexicalPath, resourcePath);
            if (!Files.exists(targetLexicalPath)) {
                return false;
            }
            Path rootRealPath = rootLexicalPath.toRealPath();
            Path targetRealPath = targetLexicalPath.toRealPath();
            ensureConfined(rootRealPath, targetRealPath, resourcePath);
            return Files.isRegularFile(targetRealPath);
        } catch (IOException _) {
            return false;
        }
    }

    private static void ensureConfined(Path rootPath, Path targetPath, String sourcePath) {
        if (!targetPath.startsWith(rootPath)) {
            throw new SecurityException("Directory traversal detected in path: " + sourcePath);
        }
    }

    public record ScanRequest(String spiServiceName, String binaryClassName) {}

    public record ScannerResult(List<String> providerSources, List<String> classSources) {}

    private record ScanHit(boolean providerSource, boolean classSource) {

        private static final ScanHit NONE = new ScanHit(false, false);
    }

    private record JarCacheKey(String absolutePath, long lastModified, long size) {}

    private record JarEntryIndex(Set<String> entryNames) {

        private boolean contains(String entryName) {
            return entryNames.contains(entryName);
        }
    }
}
