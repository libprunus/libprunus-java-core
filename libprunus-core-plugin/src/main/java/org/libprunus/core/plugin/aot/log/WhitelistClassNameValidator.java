package org.libprunus.core.plugin.aot.log;

import java.io.IOException;
import java.util.Set;
import net.bytebuddy.dynamic.ClassFileLocator;

final class WhitelistClassNameValidator {

    private static final Set<String> CORE_BUILTIN_WHITELIST_SET = Set.copyOf(RuntimeBindingAbi.CORE_BUILTIN_WHITELIST);

    private WhitelistClassNameValidator() {
        throw new UnsupportedOperationException();
    }

    static void validate(Set<String> classNames, ClassFileLocator classFileLocator) {
        for (String className : classNames) {
            if (className.isEmpty() || className.contains("[") || isPrimitiveName(className)) {
                throw new IllegalStateException("Invalid whitelist class name: " + className);
            }
            if (CORE_BUILTIN_WHITELIST_SET.contains(className)) {
                continue;
            }
            try {
                if (!classFileLocator.locate(className).isResolved()) {
                    throw new IllegalStateException("Whitelist class cannot be resolved: " + className);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to resolve whitelist class: " + className, e);
            }
        }
    }

    private static boolean isPrimitiveName(String name) {
        return switch (name) {
            case "boolean", "byte", "char", "short", "int", "long", "float", "double", "void" -> true;
            default -> false;
        };
    }
}
