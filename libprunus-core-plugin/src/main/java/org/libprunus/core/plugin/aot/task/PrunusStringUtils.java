package org.libprunus.core.plugin.aot.task;

import org.jspecify.annotations.Nullable;

final class PrunusStringUtils {

    private PrunusStringUtils() {
        throw new UnsupportedOperationException();
    }

    public static @Nullable String normalize(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
