package org.libprunus.core.plugin.aot;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class BindingIdSanitizer {

    private static final Pattern INVALID_PACKAGE_CHARS = Pattern.compile("[^a-zA-Z0-9_$]");

    private static final Set<String> JAVA_KEYWORDS = Set.of(
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
            "while");

    private static final Set<String> JAVA_RESERVED_LITERALS = Set.of("true", "false", "null", "_");

    private static final Set<String> JPMS_RESTRICTED_KEYWORDS =
            Set.of("module", "requires", "transitive", "uses", "with", "to");

    private static final Set<String> CONTEXTUAL_KEYWORDS = Set.of("var", "yield", "record", "sealed");

    private static final Set<String> RESERVED_SEGMENTS = Stream.of(
                    JAVA_KEYWORDS, JAVA_RESERVED_LITERALS, JPMS_RESTRICTED_KEYWORDS, CONTEXTUAL_KEYWORDS)
            .flatMap(Set::stream)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private BindingIdSanitizer() {
        throw new UnsupportedOperationException();
    }

    public static String sanitizeForPackageSegment(String bindingId) {
        Objects.requireNonNull(bindingId, "bindingId");
        String normalized = bindingId.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("bindingId must not be blank");
        }
        String sanitized = INVALID_PACKAGE_CHARS.matcher(normalized).replaceAll("_");
        boolean requiresHashSuffix = !sanitized.equals(normalized);
        // WHY: after INVALID_PACKAGE_CHARS scrubbing, only a leading digit can still fail
        // isJavaIdentifierStart — every other illegal start char has already become '_'.
        if (!Character.isJavaIdentifierStart(sanitized.charAt(0))) {
            sanitized = "_" + sanitized;
            requiresHashSuffix = true;
        }
        if (RESERVED_SEGMENTS.contains(sanitized)) {
            sanitized = sanitized + "_";
            requiresHashSuffix = true;
        }
        if (requiresHashSuffix) {
            sanitized = sanitized + "_" + ShortStableHash.of(normalized);
        }
        return sanitized;
    }
}
