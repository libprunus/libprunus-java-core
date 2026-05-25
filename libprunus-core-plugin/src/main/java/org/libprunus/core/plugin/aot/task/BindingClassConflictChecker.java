package org.libprunus.core.plugin.aot.task;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class BindingClassConflictChecker {

    private BindingClassConflictChecker() {
        throw new UnsupportedOperationException();
    }

    public static Optional<String> checkSpiDescriptorUniqueness(List<String> spiSourceJars) {
        Objects.requireNonNull(spiSourceJars, "spiSourceJars");
        List<String> unique = List.copyOf(new LinkedHashSet<>(spiSourceJars));
        if (unique.size() > 1) {
            return Optional.of("Multiple SPI service descriptors for LogConfig found in: " + unique);
        }
        return Optional.empty();
    }

    public static Optional<String> checkBindingClassUniqueness(String bindingClass, List<String> sourceJars) {
        Objects.requireNonNull(bindingClass, "bindingClass");
        Objects.requireNonNull(sourceJars, "sourceJars");
        List<String> unique = List.copyOf(new LinkedHashSet<>(sourceJars));
        if (unique.size() > 1) {
            return Optional.of("Binding class " + bindingClass + " found in multiple jars: " + unique);
        }
        return Optional.empty();
    }

    public static Optional<String> checkBindingClassPresent(String bindingClass, boolean found) {
        Objects.requireNonNull(bindingClass, "bindingClass");
        if (!found) {
            return Optional.of("Binding class " + bindingClass + " not found in classpath");
        }
        return Optional.empty();
    }
}
