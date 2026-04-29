package org.libprunus.core.log.runtime;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ToStringWhitelistClassifier {

    private static final Class<?>[] CUSTOM_TYPES =
            sanitize(AotToStringWhitelistRegistry.resolveToStringWhitelistTypes());

    private static final ClassValue<Boolean> WHITELIST_CACHE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return resolveToStringWhitelistStatus(type);
        }
    };

    private ToStringWhitelistClassifier() {
        throw new UnsupportedOperationException();
    }

    public static boolean isToStringWhitelisted(Class<?> type) {
        return WHITELIST_CACHE.get(Objects.requireNonNull(type, "type must not be null"));
    }

    private static boolean resolveToStringWhitelistStatus(Class<?> type) {
        if (Enum.class.isAssignableFrom(type)) {
            return true;
        }
        if (Number.class.isAssignableFrom(type)) {
            return true;
        }
        if (CharSequence.class.isAssignableFrom(type)) {
            return true;
        }
        if (TemporalAccessor.class.isAssignableFrom(type)) {
            return true;
        }
        if (type == Boolean.class
                || type == Character.class
                || type == UUID.class
                || type == Date.class
                || type == Class.class) {
            return true;
        }
        for (Class<?> customType : CUSTOM_TYPES) {
            if (customType.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }

    private static Class<?>[] sanitize(Class<?>[] configuredTypes) {
        if (configuredTypes == null || configuredTypes.length == 0) {
            return new Class<?>[0];
        }
        List<Class<?>> filtered = new ArrayList<>(configuredTypes.length);
        for (Class<?> type : configuredTypes) {
            if (type != null) {
                filtered.add(type);
            }
        }
        return filtered.toArray(Class<?>[]::new);
    }
}
