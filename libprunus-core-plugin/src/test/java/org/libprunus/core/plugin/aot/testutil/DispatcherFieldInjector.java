package org.libprunus.core.plugin.aot.testutil;

import java.lang.reflect.Field;

public final class DispatcherFieldInjector {

    private DispatcherFieldInjector() {
        throw new UnsupportedOperationException();
    }

    // Groovy `@field` operator cannot mutate `final` fields; reflection is the only path for test injection.
    public static void inject(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject field '" + fieldName + "' on " + target.getClass(), e);
        }
    }
}
