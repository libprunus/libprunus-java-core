package org.libprunus.core.plugin.aot.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipException;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;
import org.libprunus.core.plugin.aot.util.BoundedInputStream;
import org.libprunus.core.plugin.aot.util.ResourceLimitExceededException;

final class WhitelistResourceReader {

    static final long MAX_WHITELIST_RESOURCE_BYTES = 1024L * 1024L;
    static final int MAX_WHITELIST_LINE_LENGTH = 8192;

    private WhitelistResourceReader() {
        throw new UnsupportedOperationException();
    }

    static void readFrom(File entry, Set<String> target) {
        if (!entry.exists()) {
            return;
        }
        if (entry.isDirectory()) {
            Path whitelistPath = entry.toPath().resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH);
            if (Files.exists(whitelistPath)) {
                readWhitelistFile(whitelistPath, target);
            }
        } else if (entry.getName().endsWith(".jar")) {
            readWhitelistFromJar(entry, target);
        }
    }

    private static void readWhitelistFile(Path path, Set<String> target) {
        long fileSize;
        try {
            fileSize = Files.size(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect whitelist file: " + path, e);
        }
        WhitelistSource source = new WhitelistSource(path.toString(), fileSize, () -> Files.newInputStream(path));
        try {
            readBoundedWhitelist(source, target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read whitelist file: " + path, e);
        }
    }

    private static void readWhitelistFromJar(File jarFile, Set<String> target) {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry(PrunusPluginConstants.WHITELIST_RESOURCE_PATH);
            if (entry == null) {
                return;
            }
            WhitelistSource source = new WhitelistSource(
                    jarFile + "!" + entry.getName(), entry.getSize(), () -> jar.getInputStream(entry));
            readBoundedWhitelist(source, target);
        } catch (ZipException e) {
            System.err.println("[libprunus-aot] Skipping corrupt or non-ZIP JAR on classpath: " + jarFile + " ("
                    + e.getMessage() + ")");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read whitelist from jar: " + jarFile, e);
        }
    }

    private static void readBoundedWhitelist(WhitelistSource source, Set<String> target) throws IOException {
        if (source.sizeOrNegative() > MAX_WHITELIST_RESOURCE_BYTES) {
            throw new IllegalStateException("Whitelist resource is too large: source="
                    + source.displayName()
                    + ", size="
                    + source.sizeOrNegative()
                    + ", max="
                    + MAX_WHITELIST_RESOURCE_BYTES);
        }
        try (InputStream raw = source.opener().open();
                InputStream bounded = new BoundedInputStream(raw, MAX_WHITELIST_RESOURCE_BYTES, "Whitelist resource");
                BufferedReader reader = new BufferedReader(new InputStreamReader(bounded, StandardCharsets.UTF_8))) {
            readLines(reader, target, source.displayName());
        } catch (ResourceLimitExceededException e) {
            throw new IllegalStateException(
                    "Whitelist resource exceeded max bytes while reading: source="
                            + source.displayName()
                            + ", max="
                            + MAX_WHITELIST_RESOURCE_BYTES,
                    e);
        }
    }

    private static void readLines(BufferedReader reader, Set<String> target, String source) throws IOException {
        String line;
        boolean isFirstLine = true;
        while ((line = reader.readLine()) != null) {
            if (line.length() > MAX_WHITELIST_LINE_LENGTH) {
                throw new IllegalStateException("Whitelist resource line too long: source="
                        + source
                        + ", lineLength="
                        + line.length()
                        + ", max="
                        + MAX_WHITELIST_LINE_LENGTH);
            }
            if (isFirstLine && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            isFirstLine = false;
            line = line.strip();
            if (!line.isEmpty() && !line.startsWith("#")) {
                target.add(line);
            }
        }
    }

    @FunctionalInterface
    private interface IOSupplier {
        InputStream open() throws IOException;
    }

    private record WhitelistSource(String displayName, long sizeOrNegative, IOSupplier opener) {}
}
