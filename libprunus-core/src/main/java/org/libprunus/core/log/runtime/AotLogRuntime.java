package org.libprunus.core.log.runtime;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.libprunus.core.config.CoreRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AotLogRuntime {

    private static final String AOT_LOGGER_FIELD = "LIBPRUNUS_AOT_LOGGER";
    private static final int DEFAULT_MAX_RENDER_ELEMENTS = 100;
    private static final int DEFAULT_BUILDER_CAPACITY = 256;
    private static final CoreRuntimeConfig DEFAULT_CONFIG = new CoreRuntimeConfig(new LogRuntimeConfig(true));
    private static volatile AtomicReference<CoreRuntimeConfig> ACTIVE_CONFIG_REF =
            new AtomicReference<>(DEFAULT_CONFIG);

    private AotLogRuntime() {
        throw new UnsupportedOperationException();
    }

    public static void initialize(CoreRuntimeConfig initialConfig) {
        CoreRuntimeConfig config = Objects.requireNonNull(initialConfig, "initialConfig must not be null");
        ACTIVE_CONFIG_REF.set(config);
    }

    public static void linkToDataPlane(AtomicReference<CoreRuntimeConfig> configRef) {
        ACTIVE_CONFIG_REF = Objects.requireNonNull(configRef, "configRef must not be null");
    }

    public static void updateConfig(LogRuntimeConfig newConfig) {
        if (newConfig != null) {
            ACTIVE_CONFIG_REF.set(new CoreRuntimeConfig(newConfig));
        }
    }

    public static boolean isEnabled() {
        return ACTIVE_CONFIG_REF.get().log().enabled();
    }

    public static Logger condyLoggerFactory(MethodHandles.Lookup lookup, String name, Class<?> type) {
        Class<?> owner = lookup.lookupClass();
        Logger preconfigured = resolvePreconfiguredLogger(lookup, owner);
        if (preconfigured != null) {
            return preconfigured;
        }
        return LoggerFactory.getLogger(owner);
    }

    public static boolean isLevelEnabled(Logger logger, LogLevel level) {
        if (!ACTIVE_CONFIG_REF.get().log().enabled()) {
            return false;
        }
        return level.isEnabled(logger);
    }

    public static String safeObjectToString(Object value, int currentDepth, int maxDepth) {
        return safeObjectToString(value, currentDepth, DEFAULT_MAX_RENDER_ELEMENTS, maxDepth);
    }

    public static String safeObjectToString(Object value, int currentDepth, int maxElements, int maxDepth) {
        StringBuilder builder = new StringBuilder(DEFAULT_BUILDER_CAPACITY);
        appendObjectTo(builder, value, currentDepth, maxElements, maxDepth);
        return builder.toString();
    }

    public static String safeArrayToString(Object array, int maxElements, int maxDepth) {
        return safeArrayToString(array, 0, maxElements, maxDepth);
    }

    public static String safeArrayToString(Object array, int currentDepth, int maxElements, int maxDepth) {
        StringBuilder builder = new StringBuilder(DEFAULT_BUILDER_CAPACITY);
        appendArrayTo(builder, array, currentDepth, maxElements, maxDepth);
        return builder.toString();
    }

    public static void appendArrayTo(
            StringBuilder builder, Object array, int currentDepth, int maxElements, int maxDepth) {
        if (array == null || !array.getClass().isArray() || currentDepth >= maxDepth) {
            appendObjectTo(builder, array, currentDepth, maxElements, maxDepth);
            return;
        }
        int limit = Math.max(0, maxElements);
        if (array instanceof boolean[] value) {
            appendArrayTo(builder, value, currentDepth, limit, maxDepth);
            return;
        }
        if (array instanceof byte[] value) {
            appendArrayTo(builder, value, currentDepth, limit, maxDepth);
            return;
        }
        if (array instanceof char[] value) {
            appendArrayTo(builder, value, currentDepth, limit, maxDepth);
            return;
        }
        if (array instanceof short[] value) {
            appendArrayTo(builder, value, currentDepth, limit, maxDepth);
            return;
        }
        if (array instanceof int[] value) {
            appendArrayTo(builder, value, currentDepth, limit, maxDepth);
            return;
        }
        if (array instanceof long[] value) {
            appendArrayTo(builder, value, currentDepth, limit, maxDepth);
            return;
        }
        if (array instanceof float[] value) {
            appendArrayTo(builder, value, currentDepth, limit, maxDepth);
            return;
        }
        if (array instanceof double[] value) {
            appendArrayTo(builder, value, currentDepth, limit, maxDepth);
            return;
        }
        appendArrayTo(builder, (Object[]) array, currentDepth, limit, maxDepth);
    }

    public static String safeArrayToString(boolean[] value, int currentDepth, int maxElements, int maxDepth) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(DEFAULT_BUILDER_CAPACITY);
        appendArrayTo(builder, value, currentDepth, maxElements, maxDepth);
        return builder.toString();
    }

    private static void appendArrayTo(
            StringBuilder builder, boolean[] value, int currentDepth, int maxElements, int maxDepth) {
        if (currentDepth >= maxDepth) {
            builder.append("[DEPTH_LIMIT]");
            return;
        }
        appendPrimitiveTo(builder, value, maxElements);
    }

    public static String safeArrayToString(byte[] value, int currentDepth, int maxElements, int maxDepth) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(DEFAULT_BUILDER_CAPACITY);
        appendArrayTo(builder, value, currentDepth, maxElements, maxDepth);
        return builder.toString();
    }

    private static void appendArrayTo(
            StringBuilder builder, byte[] value, int currentDepth, int maxElements, int maxDepth) {
        if (currentDepth >= maxDepth) {
            builder.append("[DEPTH_LIMIT]");
            return;
        }
        appendPrimitiveTo(builder, value, maxElements);
    }

    public static String safeArrayToString(char[] value, int currentDepth, int maxElements, int maxDepth) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(DEFAULT_BUILDER_CAPACITY);
        appendArrayTo(builder, value, currentDepth, maxElements, maxDepth);
        return builder.toString();
    }

    private static void appendArrayTo(
            StringBuilder builder, char[] value, int currentDepth, int maxElements, int maxDepth) {
        if (currentDepth >= maxDepth) {
            builder.append("[DEPTH_LIMIT]");
            return;
        }
        appendPrimitiveTo(builder, value, maxElements);
    }

    public static String safeArrayToString(short[] value, int currentDepth, int maxElements, int maxDepth) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(DEFAULT_BUILDER_CAPACITY);
        appendArrayTo(builder, value, currentDepth, maxElements, maxDepth);
        return builder.toString();
    }

    private static void appendArrayTo(
            StringBuilder builder, short[] value, int currentDepth, int maxElements, int maxDepth) {
        if (currentDepth >= maxDepth) {
            builder.append("[DEPTH_LIMIT]");
            return;
        }
        appendPrimitiveTo(builder, value, maxElements);
    }

    public static String safeArrayToString(int[] value, int currentDepth, int maxElements, int maxDepth) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(DEFAULT_BUILDER_CAPACITY);
        appendArrayTo(builder, value, currentDepth, maxElements, maxDepth);
        return builder.toString();
    }

    private static void appendArrayTo(
            StringBuilder builder, int[] value, int currentDepth, int maxElements, int maxDepth) {
        if (currentDepth >= maxDepth) {
            builder.append("[DEPTH_LIMIT]");
            return;
        }
        appendPrimitiveTo(builder, value, maxElements);
    }

    public static String safeArrayToString(long[] value, int currentDepth, int maxElements, int maxDepth) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(DEFAULT_BUILDER_CAPACITY);
        appendArrayTo(builder, value, currentDepth, maxElements, maxDepth);
        return builder.toString();
    }

    private static void appendArrayTo(
            StringBuilder builder, long[] value, int currentDepth, int maxElements, int maxDepth) {
        if (currentDepth >= maxDepth) {
            builder.append("[DEPTH_LIMIT]");
            return;
        }
        appendPrimitiveTo(builder, value, maxElements);
    }

    public static String safeArrayToString(float[] value, int currentDepth, int maxElements, int maxDepth) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(DEFAULT_BUILDER_CAPACITY);
        appendArrayTo(builder, value, currentDepth, maxElements, maxDepth);
        return builder.toString();
    }

    private static void appendArrayTo(
            StringBuilder builder, float[] value, int currentDepth, int maxElements, int maxDepth) {
        if (currentDepth >= maxDepth) {
            builder.append("[DEPTH_LIMIT]");
            return;
        }
        appendPrimitiveTo(builder, value, maxElements);
    }

    public static String safeArrayToString(double[] value, int currentDepth, int maxElements, int maxDepth) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(DEFAULT_BUILDER_CAPACITY);
        appendArrayTo(builder, value, currentDepth, maxElements, maxDepth);
        return builder.toString();
    }

    private static void appendArrayTo(
            StringBuilder builder, double[] value, int currentDepth, int maxElements, int maxDepth) {
        if (currentDepth >= maxDepth) {
            builder.append("[DEPTH_LIMIT]");
            return;
        }
        appendPrimitiveTo(builder, value, maxElements);
    }

    public static String safeArrayToString(Object[] value, int currentDepth, int maxElements, int maxDepth) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(DEFAULT_BUILDER_CAPACITY);
        appendArrayTo(builder, value, currentDepth, maxElements, maxDepth);
        return builder.toString();
    }

    private static void appendArrayTo(
            StringBuilder builder, Object[] value, int currentDepth, int maxElements, int maxDepth) {
        if (currentDepth >= maxDepth) {
            builder.append("[DEPTH_LIMIT]");
            return;
        }
        int limit = Math.min(value.length, maxElements);
        builder.append('[');
        if (limit > 0) {
            appendContainedValue(builder, value[0], currentDepth, maxElements, maxDepth);
            for (int index = 1; index < limit; index++) {
                builder.append(", ");
                appendContainedValue(builder, value[index], currentDepth, maxElements, maxDepth);
            }
        }
        appendTruncation(builder, value.length, limit);
        builder.append(']');
    }

    private static void appendCollectionTo(
            StringBuilder builder, Collection<?> value, int currentDepth, int maxElements, int maxDepth) {
        if (currentDepth >= maxDepth) {
            builder.append("[DEPTH_LIMIT]");
            return;
        }
        int limit = Math.max(0, maxElements);
        builder.append('[');
        var it = value.iterator();
        int count = 0;
        if (it.hasNext() && limit > 0) {
            appendContainedValue(builder, it.next(), currentDepth, maxElements, maxDepth);
            count = 1;
            while (it.hasNext() && count < limit) {
                builder.append(", ");
                appendContainedValue(builder, it.next(), currentDepth, maxElements, maxDepth);
                count++;
            }
        }
        appendTruncation(builder, value.size(), count);
        builder.append(']');
    }

    private static void appendMapTo(
            StringBuilder builder, Map<?, ?> value, int currentDepth, int maxElements, int maxDepth) {
        if (currentDepth >= maxDepth) {
            builder.append("[DEPTH_LIMIT]");
            return;
        }
        int limit = Math.max(0, maxElements);
        builder.append('{');
        var it = value.entrySet().iterator();
        int count = 0;
        if (it.hasNext() && limit > 0) {
            Map.Entry<?, ?> entry = it.next();
            appendContainedValue(builder, entry.getKey(), currentDepth, maxElements, maxDepth);
            builder.append('=');
            appendContainedValue(builder, entry.getValue(), currentDepth, maxElements, maxDepth);
            count = 1;
            while (it.hasNext() && count < limit) {
                builder.append(", ");
                entry = it.next();
                appendContainedValue(builder, entry.getKey(), currentDepth, maxElements, maxDepth);
                builder.append('=');
                appendContainedValue(builder, entry.getValue(), currentDepth, maxElements, maxDepth);
                count++;
            }
        }
        appendTruncation(builder, value.size(), count);
        builder.append('}');
    }

    private static void appendContainedValue(
            StringBuilder builder, Object value, int currentDepth, int maxElements, int maxDepth) {
        appendObjectTo(builder, value, currentDepth + 1, maxElements, maxDepth);
    }

    public static void appendObjectTo(
            StringBuilder builder, Object value, int currentDepth, int maxElements, int maxDepth) {
        if (value == null) {
            builder.append("null");
            return;
        }
        if (currentDepth >= maxDepth) {
            builder.append("[DEPTH_LIMIT]");
            return;
        }
        if (value.getClass().isArray()) {
            appendArrayTo(builder, value, currentDepth, maxElements, maxDepth);
            return;
        }
        if (value instanceof AotLoggable loggable) {
            loggable._libprunus_render(builder, currentDepth + 1);
            return;
        }
        if (value instanceof Collection<?> collection) {
            appendCollectionTo(builder, collection, currentDepth, maxElements, maxDepth);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            appendMapTo(builder, map, currentDepth, maxElements, maxDepth);
            return;
        }
        if (ToStringWhitelistClassifier.isToStringWhitelisted(value.getClass())) {
            appendSafeScalarTo(builder, value);
            return;
        }
        appendIdentityTo(builder, value);
    }

    private static void appendSafeScalarTo(StringBuilder builder, Object value) {
        if (value instanceof CharSequence sequence) {
            builder.append(sequence);
            return;
        }
        if (value instanceof Boolean bool) {
            builder.append(bool.booleanValue());
            return;
        }
        if (value instanceof Character character) {
            builder.append(character.charValue());
            return;
        }
        if (value instanceof Enum<?> enumValue) {
            builder.append(enumValue.name());
            return;
        }
        if (value instanceof Class<?> type) {
            builder.append("class ").append(type.getName());
            return;
        }
        builder.append(value.toString());
    }

    private static void appendIdentityTo(StringBuilder builder, Object value) {
        builder.append(value.getClass().getName())
                .append('@')
                .append(Integer.toHexString(System.identityHashCode(value)));
    }

    private static void appendPrimitiveTo(StringBuilder builder, boolean[] value, int maxElements) {
        int limit = Math.min(value.length, Math.max(0, maxElements));
        builder.append('[');
        if (limit > 0) {
            builder.append(value[0]);
            for (int index = 1; index < limit; index++) {
                builder.append(", ");
                builder.append(value[index]);
            }
        }
        appendTruncation(builder, value.length, limit);
        builder.append(']');
    }

    private static void appendPrimitiveTo(StringBuilder builder, byte[] value, int maxElements) {
        int limit = Math.min(value.length, Math.max(0, maxElements));
        builder.append('[');
        if (limit > 0) {
            builder.append(value[0]);
            for (int index = 1; index < limit; index++) {
                builder.append(", ");
                builder.append(value[index]);
            }
        }
        appendTruncation(builder, value.length, limit);
        builder.append(']');
    }

    private static void appendPrimitiveTo(StringBuilder builder, char[] value, int maxElements) {
        int limit = Math.min(value.length, Math.max(0, maxElements));
        builder.append('[');
        if (limit > 0) {
            builder.append(value[0]);
            for (int index = 1; index < limit; index++) {
                builder.append(", ");
                builder.append(value[index]);
            }
        }
        appendTruncation(builder, value.length, limit);
        builder.append(']');
    }

    private static void appendPrimitiveTo(StringBuilder builder, short[] value, int maxElements) {
        int limit = Math.min(value.length, Math.max(0, maxElements));
        builder.append('[');
        if (limit > 0) {
            builder.append(value[0]);
            for (int index = 1; index < limit; index++) {
                builder.append(", ");
                builder.append(value[index]);
            }
        }
        appendTruncation(builder, value.length, limit);
        builder.append(']');
    }

    private static void appendPrimitiveTo(StringBuilder builder, int[] value, int maxElements) {
        int limit = Math.min(value.length, Math.max(0, maxElements));
        builder.append('[');
        if (limit > 0) {
            builder.append(value[0]);
            for (int index = 1; index < limit; index++) {
                builder.append(", ");
                builder.append(value[index]);
            }
        }
        appendTruncation(builder, value.length, limit);
        builder.append(']');
    }

    private static void appendPrimitiveTo(StringBuilder builder, long[] value, int maxElements) {
        int limit = Math.min(value.length, Math.max(0, maxElements));
        builder.append('[');
        if (limit > 0) {
            builder.append(value[0]);
            for (int index = 1; index < limit; index++) {
                builder.append(", ");
                builder.append(value[index]);
            }
        }
        appendTruncation(builder, value.length, limit);
        builder.append(']');
    }

    private static void appendPrimitiveTo(StringBuilder builder, float[] value, int maxElements) {
        int limit = Math.min(value.length, Math.max(0, maxElements));
        builder.append('[');
        if (limit > 0) {
            builder.append(value[0]);
            for (int index = 1; index < limit; index++) {
                builder.append(", ");
                builder.append(value[index]);
            }
        }
        appendTruncation(builder, value.length, limit);
        builder.append(']');
    }

    private static void appendPrimitiveTo(StringBuilder builder, double[] value, int maxElements) {
        int limit = Math.min(value.length, Math.max(0, maxElements));
        builder.append('[');
        if (limit > 0) {
            builder.append(value[0]);
            for (int index = 1; index < limit; index++) {
                builder.append(", ");
                builder.append(value[index]);
            }
        }
        appendTruncation(builder, value.length, limit);
        builder.append(']');
    }

    private static void appendTruncation(StringBuilder builder, int size, int limit) {
        if (size > limit) {
            if (limit > 0) {
                builder.append(", ");
            }
            builder.append("... (truncated, size=").append(size).append(')');
        }
    }

    private static Logger resolvePreconfiguredLogger(MethodHandles.Lookup lookup, Class<?> owner) {
        try {
            return (Logger) lookup.findStaticGetter(owner, AOT_LOGGER_FIELD, Logger.class)
                    .invoke();
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return null;
        } catch (Error error) {
            throw error;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
