package org.libprunus.core.plugin.aot.task;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class BindingClassConflictChecker {

    private BindingClassConflictChecker() {
        throw new UnsupportedOperationException();
    }

    public static Optional<String> checkSpiDescriptorUniqueness(List<String> spiSourceJars) {
        Objects.requireNonNull(spiSourceJars, "spiSourceJars");
        if (spiSourceJars.size() > 1) {
            return Optional.of("Multiple SPI service descriptors for LogConfig found in: " + spiSourceJars);
        }
        return Optional.empty();
    }

    public static Optional<String> checkBindingClassUniqueness(String bindingClass, List<String> sourceJars) {
        Objects.requireNonNull(bindingClass, "bindingClass");
        Objects.requireNonNull(sourceJars, "sourceJars");
        if (sourceJars.size() > 1) {
            return Optional.of("Binding class " + bindingClass + " found in multiple jars: " + sourceJars);
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
