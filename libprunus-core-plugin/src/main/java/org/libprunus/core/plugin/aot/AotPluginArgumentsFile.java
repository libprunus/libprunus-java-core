package org.libprunus.core.plugin.aot;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public final class AotPluginArgumentsFile {

    private static final String VERSION_KEY = "version";
    private static final String VERSION_VALUE = "1";

    private AotPluginArgumentsFile() {}

    public static void write(Path path, AotPluginArguments arguments) {
        writeLines(path, toValues(arguments));
    }

    public static AotPluginArguments read(Path path) {
        return fromValues(readLines(path));
    }

    private static AotPluginArguments fromValues(Map<String, String> values) {
        String version = required(values, VERSION_KEY);
        if (!VERSION_VALUE.equals(version)) {
            throw new IllegalStateException("Unsupported config version: " + version);
        }

        boolean enabled = Boolean.parseBoolean(required(values, "enabled"));

        AotLogArguments logArguments = new AotLogArguments(
                Boolean.parseBoolean(required(values, "log.enabled")),
                readList(values, "log.targetClassSuffixes"),
                readList(values, "log.pojoSuffixes"),
                required(values, "log.classNameFormat"),
                required(values, "log.enterLogLevel"),
                required(values, "log.exitLogLevel"),
                Boolean.parseBoolean(required(values, "log.handleInaccessibleField")),
                Integer.parseInt(required(values, "log.maxToStringDepth")),
                readList(values, "log.toStringWhitelist"));

        return new AotPluginArguments(
                enabled, readList(values, "basePackages"), readList(values, "excludePackages"), logArguments);
    }

    private static TreeMap<String, String> toValues(AotPluginArguments arguments) {
        TreeMap<String, String> values = new TreeMap<>();
        values.put(VERSION_KEY, VERSION_VALUE);
        values.put("enabled", Boolean.toString(arguments.enabled()));
        writeList(values, "basePackages", arguments.basePackages());
        writeList(values, "excludePackages", arguments.excludePackages());

        AotLogArguments log = arguments.log();
        values.put("log.enabled", Boolean.toString(log.enabled()));
        writeList(values, "log.targetClassSuffixes", log.targetClassSuffixes());
        writeList(values, "log.pojoSuffixes", log.pojoSuffixes());
        values.put("log.classNameFormat", log.classNameFormat());
        values.put("log.enterLogLevel", log.enterLogLevel());
        values.put("log.exitLogLevel", log.exitLogLevel());
        values.put("log.handleInaccessibleField", Boolean.toString(log.handleInaccessibleField()));
        values.put("log.maxToStringDepth", Integer.toString(log.maxToStringDepth()));
        writeList(values, "log.toStringWhitelist", log.toStringWhitelist());
        return values;
    }

    private static void writeList(Map<String, String> values, String keyPrefix, List<String> list) {
        values.put(keyPrefix + ".size", Integer.toString(list.size()));
        for (int index = 0; index < list.size(); index++) {
            values.put(keyPrefix + "." + index, list.get(index));
        }
    }

    private static List<String> readList(Map<String, String> values, String keyPrefix) {
        int size = Integer.parseInt(required(values, keyPrefix + ".size"));
        List<String> list = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            list.add(required(values, keyPrefix + "." + index));
        }
        return list;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing config key: " + key);
        }
        return value;
    }

    private static void writeLines(Path path, TreeMap<String, String> values) {
        Path tempFile = null;
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tempDirectory =
                    parent != null ? parent : path.toAbsolutePath().normalize().getParent();
            tempFile = Files.createTempFile(tempDirectory, path.getFileName().toString(), ".tmp");
            writeProperties(tempFile, values);
            try {
                Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                replaceWithFileLock(path, tempFile);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write AOT config file: " + path, exception);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void replaceWithFileLock(Path path, Path tempFile) throws IOException {
        forceFile(tempFile);
        Path lockPath = path.resolveSibling(path.getFileName().toString() + ".lock");
        try {
            try (FileChannel channel =
                    FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                FileLock lock = channel.lock();
                try {
                    Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
                    forceParentDirectory(path);
                } finally {
                    lock.release();
                }
            }
        } finally {
            try {
                Files.deleteIfExists(lockPath);
            } catch (IOException ignored) {
            }
        }
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void forceParentDirectory(Path file) {
        Path parent = file.getParent();
        if (parent == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(parent, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
        }
    }

    private static Map<String, String> readLines(Path path) {
        try {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            TreeMap<String, String> values = new TreeMap<>();
            for (String key : properties.stringPropertyNames()) {
                values.put(key, properties.getProperty(key));
            }
            return values;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read AOT config file: " + path, exception);
        }
    }

    private static void writeProperties(Path target, TreeMap<String, String> values) throws IOException {
        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                writer.write(escapeKey(entry.getKey()) + "=" + escapeValue(entry.getValue()) + "\n");
            }
        }
    }

    private static String escapeKey(String key) {
        return key.replace("\\", "\\\\")
                .replace("=", "\\=")
                .replace(":", "\\:")
                .replace("#", "\\#")
                .replace("!", "\\!")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String escapeValue(String value) {
        // TODO Define the exchange-format contract for leading whitespace in machine-written property values. The
        // current writer favors deterministic, timestamp-free files, while Properties.load consumes unescaped leading
        // spaces. Either align escaping with reader semantics or narrow the allowed value shape so round-trip
        // guarantees remain explicit.
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
