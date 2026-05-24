package org.libprunus.core.plugin.aot.task;

final class PrunusStringUtils {

    private PrunusStringUtils() {
        throw new UnsupportedOperationException();
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
