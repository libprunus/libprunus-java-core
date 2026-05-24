package org.libprunus.core.plugin.aot.task;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.libprunus.core.plugin.aot.BindingIdSanitizer;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;

final class BindingClassSelector {

    private static final Pattern FQCN_PATTERN =
            Pattern.compile("^([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*$");
    private static final List<String> RESERVED_NAMESPACE_PREFIXES =
            List.of("java.", "javax.", "jdk.", "sun.", "com.sun.");

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

    public static SelectionResult select(String explicitBindingClass, String defaultBindingClass) {
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
            return new SelectionResult(stripped, true);
        }
        return new SelectionResult(defaultBindingClass, false);
    }

    private static boolean isReservedNamespace(String fqcn) {
        String normalized = fqcn.toLowerCase(Locale.ROOT);
        return RESERVED_NAMESPACE_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    public record SelectionResult(String bindingClassName, boolean explicit) {}
}
