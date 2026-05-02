package org.libprunus.core.plugin.aot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;

final class AotToStringWhitelistGenerator {

    private static final String GENERATED_PACKAGE = "org.libprunus.generated";
    private static final String GENERATED_SIMPLE_NAME = "AotToStringWhitelist";

    private AotToStringWhitelistGenerator() {}

    static void generateSource(Path generatedSourceDirectory, List<String> toStringWhitelist) {
        Path packageDir = generatedSourceDirectory.resolve("org/libprunus/generated");
        Path sourceFile = packageDir.resolve(GENERATED_SIMPLE_NAME + ".java");
        if (toStringWhitelist == null || toStringWhitelist.isEmpty()) {
            deleteIfExists(sourceFile);
            return;
        }

        String source = buildSource(toStringWhitelist);
        writeSource(packageDir, sourceFile, source);
    }

    private static String buildSource(List<String> configuredTypes) {
        String joined = configuredTypes.stream()
                .map(className -> className.replace('$', '.') + ".class")
                .collect(Collectors.joining(", "));
        return "package " + GENERATED_PACKAGE + ";\n"
                + "public final class " + GENERATED_SIMPLE_NAME + " {\n"
                + "    private " + GENERATED_SIMPLE_NAME + "() {}\n"
                + "    public static Class<?>[] get() {\n"
                + "        return new Class<?>[] { " + joined + " };\n"
                + "    }\n"
                + "}\n";
    }

    private static void writeSource(Path packageDir, Path sourceFile, String source) {
        try {
            Files.createDirectories(packageDir);
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new GradleException("Failed to generate AOT toStringWhitelist source", exception);
        }
    }

    private static void deleteIfExists(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new GradleException("Failed to delete stale generated source: " + file, exception);
        }
    }
}
