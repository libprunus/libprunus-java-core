package org.libprunus.core.log.runtime;

import java.lang.reflect.Method;

final class AotToStringWhitelistRegistry {

    private static final String GENERATED_CLASS_NAME = "org.libprunus.generated.AotToStringWhitelist";
    private static final String GENERATED_METHOD_NAME = "get";
    private static final Class<?>[] EMPTY = new Class<?>[0];
    private static final Class<?>[] CUSTOM_TYPES = loadToStringWhitelistTypes();

    private AotToStringWhitelistRegistry() {}

    static Class<?>[] resolveToStringWhitelistTypes() {
        return CUSTOM_TYPES;
    }

    private static Class<?>[] loadToStringWhitelistTypes() {
        ClassLoader registryLoader = AotToStringWhitelistRegistry.class.getClassLoader();
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Class<?>[] loaded = tryLoad(registryLoader);
        if (loaded.length > 0) {
            return loaded;
        }
        if (contextLoader != registryLoader) {
            loaded = tryLoad(contextLoader);
            if (loaded.length > 0) {
                return loaded;
            }
        }
        return EMPTY;
    }

    private static Class<?>[] tryLoad(ClassLoader classLoader) {
        try {
            Class<?> generatedType = Class.forName(GENERATED_CLASS_NAME, true, classLoader);
            Method method = generatedType.getMethod(GENERATED_METHOD_NAME);
            Object value = method.invoke(null);
            if (value instanceof Class<?>[] types) {
                return types.clone();
            }
            return EMPTY;
        } catch (ClassNotFoundException ignored) {
            return EMPTY;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return EMPTY;
        }
    }
}
