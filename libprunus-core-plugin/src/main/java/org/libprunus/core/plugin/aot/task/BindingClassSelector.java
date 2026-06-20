package org.libprunus.core.plugin.aot.task;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.libprunus.core.plugin.aot.BindingIdSanitizer;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;

final class BindingClassSelector {

    private static final Pattern FQCN_PATTERN =
            Pattern.compile("^([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*$");
    private static final List<String> RESERVED_NAMESPACE_PREFIXES =
            List.of("java.", "javax.", "jdk.", "sun.", "com.sun.");
    private static final Set<String> RESERVED_JAVA_KEYWORDS = Set.of(
            "abstract",
            "assert",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "extends",
            "final",
            "finally",
            "float",
            "for",
            "goto",
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "native",
            "new",
            "package",
            "private",
            "protected",
            "public",
            "return",
            "short",
            "static",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "transient",
            "try",
            "void",
            "volatile",
            "while",
            "true",
            "false",
            "null",
            "_");

    private BindingClassSelector() {
        throw new UnsupportedOperationException();
    }

    public static String defaultBindingClassName(String bindingId) {
        String sanitizedId = BindingIdSanitizer.sanitizeForPackageSegment(bindingId);
        return PrunusPluginConstants.GENERATED_AOT_PACKAGE
                + "."
                + sanitizedId
                + "."
                + PrunusPluginConstants.GENERATED_AOT_BINDING_IMPL_SIMPLE_NAME;
    }

    public static SelectionResult select(@Nullable String explicitBindingClass, String defaultBindingClass) {
        Objects.requireNonNull(defaultBindingClass, "defaultBindingClass");
        if (explicitBindingClass != null && !explicitBindingClass.isBlank()) {
            String stripped = explicitBindingClass.strip();
            if (!FQCN_PATTERN.matcher(stripped).matches()) {
                throw new IllegalArgumentException("Explicit binding class is not a valid Java FQCN: " + stripped);
            }
            if (isReservedNamespace(stripped)) {
                throw new IllegalArgumentException(
                        "Explicit binding class uses a reserved package namespace: " + stripped);
            }
            if (containsReservedKeywordSegment(stripped)) {
                throw new IllegalArgumentException(
                        "Explicit binding class contains a Java reserved keyword segment: " + stripped);
            }
            return new SelectionResult(stripped, true);
        }
        return new SelectionResult(defaultBindingClass, false);
    }

    private static boolean isReservedNamespace(String fqcn) {
        String normalized = fqcn.toLowerCase(Locale.ROOT);
        return RESERVED_NAMESPACE_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    private static boolean containsReservedKeywordSegment(String fqcn) {
        for (String segment : fqcn.split("\\.", -1)) {
            if (RESERVED_JAVA_KEYWORDS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    public record SelectionResult(String bindingClassName, boolean explicit) {}
}
