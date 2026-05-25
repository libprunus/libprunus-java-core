package org.libprunus.core.log.runtime;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Thread-local-pooled buffer that renders log message content under a strict total-length bound.
 * Acquire and release via {@link StringBuilderPool}, not direct construction.
 *
 * <h2>Length bound</h2>
 * At every externally observable point, {@code builder.length() <= maxMessageLength}. The bound
 * is strict, not best-effort — see {@link org.libprunus.core.log.annotation.MaxMessageLength} for
 * the value-domain rules.
 *
 * <h2>Truncation state</h2>
 * {@link #isTruncated()} is the authoritative truncation signal. Once it returns {@code true},
 * every subsequent {@code append*}, {@link #render(Object)}, and {@link #prependSeparator}
 * returns {@code false} without mutating the builder.
 *
 * <h2>Marker taxonomy</h2>
 * Five marker literals may appear in the output. Marker presence and content are
 * non-authoritative — consumers must read {@link #isTruncated()}, not parse marker text.
 * <ul>
 *   <li>{@code "..."} — value-level cut emitted by {@code triggerTruncation*} when a scalar /
 *       {@link CharSequence} / array append overflows.
 *   <li>{@code "...[SOE]"} ({@link #STACK_OVERFLOW_MARKER}) — {@link StackOverflowError} recovery
 *       in {@link #render(Object)} and {@link #appendFallbackString(Object)}.
 *   <li>{@code "...[CME]"} ({@link #CONCURRENT_MODIFICATION_MARKER}) — concurrent-collapse
 *       symptom absorbed by {@link CollectionRenderer} / {@link MapRenderer}; not written from
 *       this class directly.
 *   <li>{@code "...[MAX_DEPTH]"} ({@link #MAX_DEPTH_MARKER}) — emitted by
 *       {@link #enterRenderDepth()} when {@link #MAX_RENDER_DEPTH} container frames are already
 *       on the stack. Scalars are never depth-limited.
 *   <li>{@code "...[TRUNCATED])"} ({@link #RENDER_TRUNCATION_MARKER}) — message-level closure
 *       marker written by {@link #markRenderTruncation()} from AOT-woven render paths; rewinds
 *       any preceding {@code "..."} to produce a paren-balanced closing form.
 * </ul>
 *
 * <h2>Idempotency</h2>
 * {@link #forceAppendAuditMarker(String)} (and therefore {@link #markRenderTruncation()}) honors
 * a per-context first-wins latch; later calls are no-ops regardless of marker text.
 *
 * <h2>Tradeoff order under pressure</h2>
 * <ol>
 *   <li>Length upper bound — strict, never relaxed.
 *   <li>UTF-16 encoding legality (no orphan surrogate {@code char} in the output) — strict.
 *   <li>{@link #isTruncated()} state correctness — strict.
 *   <li>Marker presence / content — may be partial or absent at very small budgets.
 *   <li>ASCII {@code \\uXXXX} escapes produced by {@link #append(char)} on isolated surrogate
 *       chars are treated as plain text by the cut algorithm and may split mid-sequence.
 *   <li>Field values — clipped at the cut point. This is the primary intent of truncation.
 * </ol>
 *
 * <h2>Type-renderer caching</h2>
 * For each concrete value class, the renderer chosen by the first {@code appendObjectTo}
 * dispatch is memoized in a {@link ClassValue}-backed cache for the lifetime of the
 * loading classloader. Later changes to the active whitelist binding
 * ({@link LogRuntime#globalConfigBinding()}) do <em>not</em> invalidate that decision.
 * This matches the once-only semantics of {@link LogRuntime#initializeBinding};
 * multi-classloader / plugin / hot-reload scenarios that need a different whitelist
 * must load this class under a different classloader.
 *
 * <p>Not safe for concurrent use; each instance is scoped to a single thread by
 * {@link StringBuilderPool}.
 */
public final class StringBuilderWithContext {
    private static final String TRUNCATION_SUFFIX = "...";
    private static final int TRUNCATION_SUFFIX_LENGTH = TRUNCATION_SUFFIX.length();
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    static final String STACK_OVERFLOW_MARKER = "...[SOE]";
    static final String CONCURRENT_MODIFICATION_MARKER = "...[CME]";
    static final String RENDER_TRUNCATION_MARKER = "...[TRUNCATED])";

    private static final Map<Class<?>, NonSealedTypeRenderer> INLINE_EXACT_RENDERERS_CANDIDATE =
            buildInlineExactRenderersCandidate();
    private static final Map<Class<?>, NonSealedTypeRenderer> EXACT_RENDERERS = INLINE_EXACT_RENDERERS_CANDIDATE;
    private static final NonSealedTypeRenderer ENUM_RENDERER = (c, v) -> c.append(((Enum<?>) v).name());
    private static final NonSealedTypeRenderer CHAR_SEQUENCE_RENDERER = (c, v) -> c.append((CharSequence) v);
    private static final NonSealedTypeRenderer NUMBER_OR_WHITELIST_RENDERER = (c, v) -> c.appendFallbackString(v);
    private static final ClassValue<TypeRenderer> RENDERER_CACHE = new ClassValue<>() {
        @Override
        protected TypeRenderer computeValue(Class<?> type) {
            return resolveRenderer(type);
        }
    };

    static final int MAX_RENDER_DEPTH = 16;
    static final String MAX_DEPTH_MARKER = "...[MAX_DEPTH]";

    final StringBuilder builder;
    private int maxMessageLength;
    private boolean truncated;
    private boolean auditMarkerAppended;
    private int renderDepth;

    StringBuilderWithContext(StringBuilder builder) {
        this.builder = Objects.requireNonNull(builder, "builder must not be null");
    }

    /**
     * Sets the strict length budget for this context.
     *
     * <p>If the new bound is below the current {@code builder.length()}, the buffer is
     * truncated in place via the same algorithm as render-time overflow ({@code "..."}
     * suffix appended within the new bound, surrogate-pair safe), and
     * {@link #isTruncated()} becomes {@code true}. This preserves the class-level
     * "strict at every externally observable point" invariant declared above.
     *
     * @param maxMessageLength non-negative new bound.
     * @throws IllegalArgumentException if {@code maxMessageLength < 0}.
     */
    public void setMaxMessageLength(int maxMessageLength) {
        if (maxMessageLength < 0) {
            throw new IllegalArgumentException("maxMessageLength must be non-negative, got " + maxMessageLength);
        }
        this.maxMessageLength = maxMessageLength;
        if (builder.length() > maxMessageLength) {
            triggerTruncationBase();
        }
    }

    void reset(int maxMessageLength) {
        this.builder.setLength(0);
        this.maxMessageLength = maxMessageLength;
        this.truncated = false;
        this.auditMarkerAppended = false;
        this.renderDepth = 0;
    }

    public boolean isTruncated() {
        return truncated;
    }

    boolean enterRenderDepth() {
        if (truncated) return false;
        if (renderDepth >= MAX_RENDER_DEPTH) {
            forceAppendAuditMarker(MAX_DEPTH_MARKER);
            return false;
        }
        renderDepth++;
        return true;
    }

    void exitRenderDepth() {
        renderDepth--;
    }

    public void render(Object value) {
        if (truncated) return;
        try {
            appendObjectTo(value);
        } catch (Throwable error) {
            handleRenderError(this, error);
        }
    }

    boolean appendObjectTo(Object value) {
        if (truncated) return false;
        if (value == null) {
            return append("null");
        }

        Class<?> clazz = value.getClass();
        if (clazz == String.class) {
            return append((String) value);
        }
        RENDERER_CACHE.get(clazz).render(this, value);
        return !truncated;
    }

    boolean prependSeparator() {
        if (truncated) return false;
        if (maxMessageLength - builder.length() < 2) {
            return triggerTruncationBase();
        }
        builder.append(", ");
        return true;
    }

    void appendThrowableFallback(Throwable throwable) {
        if (throwable == null || truncated) return;
        if (throwable instanceof StackOverflowError) {
            forceAppendAuditMarker(STACK_OVERFLOW_MARKER);
            return;
        }
        if (append("...[")) {
            append(throwable.getClass().getName());
            append(']');
        }
    }

    public boolean append(String text) {
        if (truncated) return false;
        String normalizedText = text == null ? "null" : text;
        int allowed = maxMessageLength - builder.length();
        if (allowed <= 0) return triggerTruncationBase();
        if (normalizedText.length() <= allowed) {
            builder.append(normalizedText);
            return true;
        }
        triggerTruncation(normalizedText);
        return false;
    }

    public boolean append(CharSequence value) {
        if (truncated) return false;
        CharSequence normalizedValue = value == null ? "null" : value;
        int allowed = maxMessageLength - builder.length();
        if (allowed <= 0) return triggerTruncationBase();
        if (normalizedValue.length() <= allowed) {
            builder.append(normalizedValue);
            return true;
        }
        triggerTruncation(normalizedValue);
        return false;
    }

    public boolean append(boolean value) {
        if (truncated) return false;
        int checkpoint = builder.length();
        builder.append(value);
        if (builder.length() <= maxMessageLength) return true;
        builder.setLength(checkpoint);
        return triggerTruncationBase();
    }

    public boolean append(byte value) {
        if (truncated) return false;
        int checkpoint = builder.length();
        builder.append(value);
        if (builder.length() <= maxMessageLength) return true;
        builder.setLength(checkpoint);
        return triggerTruncationBase();
    }

    public boolean append(char value) {
        if (truncated) return false;
        if (builder.length() < maxMessageLength) {
            if (Character.isSurrogate(value)) {
                return appendSurrogateEscape(value);
            }
            builder.append(value);
            return true;
        }
        return triggerTruncationBase();
    }

    public boolean append(short value) {
        if (truncated) return false;
        int checkpoint = builder.length();
        builder.append(value);
        if (builder.length() <= maxMessageLength) return true;
        builder.setLength(checkpoint);
        return triggerTruncationBase();
    }

    public boolean append(int value) {
        if (truncated) return false;
        int checkpoint = builder.length();
        builder.append(value);
        if (builder.length() <= maxMessageLength) return true;
        builder.setLength(checkpoint);
        return triggerTruncationBase();
    }

    public boolean append(long value) {
        if (truncated) return false;
        int checkpoint = builder.length();
        builder.append(value);
        if (builder.length() <= maxMessageLength) return true;
        builder.setLength(checkpoint);
        return triggerTruncationBase();
    }

    public boolean append(float value) {
        if (truncated) return false;
        int checkpoint = builder.length();
        builder.append(value);
        if (builder.length() <= maxMessageLength) return true;
        builder.setLength(checkpoint);
        return triggerTruncationBase();
    }

    public boolean append(double value) {
        if (truncated) return false;
        int checkpoint = builder.length();
        builder.append(value);
        if (builder.length() <= maxMessageLength) return true;
        builder.setLength(checkpoint);
        return triggerTruncationBase();
    }

    boolean appendFallbackString(Object value) {
        if (truncated) return false;
        try {
            return append(String.valueOf(value));
        } catch (Throwable throwable) {
            handleRenderError(this, throwable);
            return false;
        }
    }

    public void forceAppendAuditMarker(String marker) {
        if (auditMarkerAppended) return;
        auditMarkerAppended = true;
        truncated = true;
        int markerLength = marker.length();
        if (builder.length() + markerLength <= maxMessageLength) {
            builder.append(marker);
        } else {
            int cutPoint = adjustCutPointForSurrogate(builder, Math.max(0, maxMessageLength - markerLength));
            builder.setLength(cutPoint);
            int toAppend = Math.min(markerLength, maxMessageLength - builder.length());
            if (toAppend > 0) {
                builder.append(marker, 0, adjustCutPointForSurrogate(marker, toAppend));
            }
        }
    }

    public void markRenderTruncation() {
        forceAppendAuditMarker(RENDER_TRUNCATION_MARKER);
    }

    private boolean triggerTruncationBase() {
        truncated = true;
        int targetLength = maxMessageLength - TRUNCATION_SUFFIX_LENGTH;
        if (builder.length() <= targetLength) {
            builder.append(TRUNCATION_SUFFIX);
        } else {
            int cutPoint = adjustCutPointForSurrogate(builder, Math.max(0, targetLength));
            builder.setLength(cutPoint);
            int toAppend = Math.min(TRUNCATION_SUFFIX_LENGTH, maxMessageLength - cutPoint);
            if (toAppend > 0) {
                builder.append(TRUNCATION_SUFFIX, 0, toAppend);
            }
        }
        return false;
    }

    private void triggerTruncation(CharSequence overflowText) {
        truncated = true;
        int remaining = maxMessageLength - builder.length();
        if (remaining >= TRUNCATION_SUFFIX_LENGTH) {
            int cutPoint = adjustCutPointForSurrogate(overflowText, remaining - TRUNCATION_SUFFIX_LENGTH);
            builder.append(overflowText, 0, cutPoint);
            builder.append(TRUNCATION_SUFFIX);
            return;
        }
        triggerTruncationBase();
    }

    private boolean appendSurrogateEscape(char value) {
        if (maxMessageLength - builder.length() >= 6) {
            builder.append('\\')
                    .append('u')
                    .append(HEX_DIGITS[(value >>> 12) & 0xF])
                    .append(HEX_DIGITS[(value >>> 8) & 0xF])
                    .append(HEX_DIGITS[(value >>> 4) & 0xF])
                    .append(HEX_DIGITS[value & 0xF]);
            return true;
        }
        return triggerTruncationBase();
    }

    private static int adjustCutPointForSurrogate(CharSequence value, int cutPoint) {
        if (cutPoint <= 0 || cutPoint >= value.length()) return cutPoint;
        if (Character.isHighSurrogate(value.charAt(cutPoint - 1))) return cutPoint - 1;
        return cutPoint;
    }

    void appendArrayTo(boolean[] array) {
        if (!append('[')) return;
        int length = array.length;
        if (length > 0 && append(array[0])) {
            for (int i = 1; i < length; i++) {
                if (!prependSeparator() || !append(array[i])) break;
            }
        }
        append(']');
    }

    void appendArrayTo(byte[] array) {
        if (!append('[')) return;
        int length = array.length;
        if (length > 0 && append(array[0])) {
            for (int i = 1; i < length; i++) {
                if (!prependSeparator() || !append(array[i])) break;
            }
        }
        append(']');
    }

    void appendArrayTo(char[] array) {
        if (!append('[')) return;
        int length = array.length;
        if (length > 0 && append(array[0])) {
            for (int i = 1; i < length; i++) {
                if (!prependSeparator() || !append(array[i])) break;
            }
        }
        append(']');
    }

    void appendArrayTo(short[] array) {
        if (!append('[')) return;
        int length = array.length;
        if (length > 0 && append(array[0])) {
            for (int i = 1; i < length; i++) {
                if (!prependSeparator() || !append(array[i])) break;
            }
        }
        append(']');
    }

    void appendArrayTo(int[] array) {
        if (!append('[')) return;
        int length = array.length;
        if (length > 0 && append(array[0])) {
            for (int i = 1; i < length; i++) {
                if (!prependSeparator() || !append(array[i])) break;
            }
        }
        append(']');
    }

    void appendArrayTo(long[] array) {
        if (!append('[')) return;
        int length = array.length;
        if (length > 0 && append(array[0])) {
            for (int i = 1; i < length; i++) {
                if (!prependSeparator() || !append(array[i])) break;
            }
        }
        append(']');
    }

    void appendArrayTo(float[] array) {
        if (!append('[')) return;
        int length = array.length;
        if (length > 0 && append(array[0])) {
            for (int i = 1; i < length; i++) {
                if (!prependSeparator() || !append(array[i])) break;
            }
        }
        append(']');
    }

    void appendArrayTo(double[] array) {
        if (!append('[')) return;
        int length = array.length;
        if (length > 0 && append(array[0])) {
            for (int i = 1; i < length; i++) {
                if (!prependSeparator() || !append(array[i])) break;
            }
        }
        append(']');
    }

    @Override
    public String toString() {
        return builder.toString();
    }

    public void logAndRelease(LoggingEventBuilder event) {
        try {
            event.log(builder.toString());
        } finally {
            StringBuilderPool.release(this);
        }
    }

    public static void reportLoggingFailure(String ownerAndMethod, Throwable throwable) {
        LoggingFailureReporter.instance().offer(ownerAndMethod, throwable);
    }

    /**
     * Bucket-shared error-handling policy for renderer-family {@code catch (Throwable)} blocks:
     * rethrows non-{@link StackOverflowError} {@link Error} instances unchanged, and routes every
     * other {@link Throwable} (including {@link StackOverflowError}, checked / unchecked exceptions)
     * to {@link #appendThrowableFallback(Throwable)} on {@code context}. Callers must wrap the
     * invocation in their own {@code try / finally} so {@link #exitRenderDepth()} and other cleanup
     * still runs when this method rethrows; this helper deliberately does not perform any cleanup.
     */
    static void handleRenderError(StringBuilderWithContext context, Throwable throwable) {
        if (throwable instanceof Error error && !(error instanceof StackOverflowError)) {
            throw error;
        }
        context.appendThrowableFallback(throwable);
    }

    public static void handleRenderFailure(
            String ownerAndMethod, StringBuilderWithContext stringBuilder, Throwable throwable) {
        if (stringBuilder != null) {
            StringBuilderPool.release(stringBuilder);
        }
        if (throwable instanceof Error error && !(error instanceof StackOverflowError)) {
            throw error;
        }
        reportLoggingFailure(ownerAndMethod, throwable);
    }

    public static String recoverToStringFallback(
            String ownerAndMethod, StringBuilderWithContext stringBuilder, Throwable throwable) {
        if (throwable instanceof Error error && !(error instanceof StackOverflowError)) {
            if (stringBuilder != null) StringBuilderPool.release(stringBuilder);
            throw error;
        }
        String fallback = "";
        if (stringBuilder != null) {
            try {
                fallback = stringBuilder.builder.toString();
            } catch (Throwable toStringFailure) {
                if (toStringFailure instanceof Error error && !(error instanceof StackOverflowError)) {
                    if (throwable != null) {
                        error.addSuppressed(throwable);
                    }
                    reportLoggingFailure(ownerAndMethod, error);
                    StringBuilderPool.release(stringBuilder);
                    throw error;
                }
                reportLoggingFailure(ownerAndMethod, toStringFailure);
            }
            StringBuilderPool.release(stringBuilder);
        }
        if (throwable != null) {
            reportLoggingFailure(ownerAndMethod, throwable);
        }
        return fallback;
    }

    private static Map<Class<?>, NonSealedTypeRenderer> buildInlineExactRenderersCandidate() {
        Map<Class<?>, NonSealedTypeRenderer> renderers = Map.ofEntries(
                Map.entry(boolean[].class, (c, v) -> c.appendArrayTo((boolean[]) v)),
                Map.entry(byte[].class, (c, v) -> c.appendArrayTo((byte[]) v)),
                Map.entry(char[].class, (c, v) -> c.appendArrayTo((char[]) v)),
                Map.entry(short[].class, (c, v) -> c.appendArrayTo((short[]) v)),
                Map.entry(int[].class, (c, v) -> c.appendArrayTo((int[]) v)),
                Map.entry(long[].class, (c, v) -> c.appendArrayTo((long[]) v)),
                Map.entry(float[].class, (c, v) -> c.appendArrayTo((float[]) v)),
                Map.entry(double[].class, (c, v) -> c.appendArrayTo((double[]) v)),
                Map.entry(Integer.class, (c, v) -> c.append(((Integer) v).intValue())),
                Map.entry(Long.class, (c, v) -> c.append(((Long) v).longValue())),
                Map.entry(Double.class, (c, v) -> c.append(((Double) v).doubleValue())),
                Map.entry(Float.class, (c, v) -> c.append(((Float) v).floatValue())),
                Map.entry(Short.class, (c, v) -> c.append(((Short) v).shortValue())),
                Map.entry(Byte.class, (c, v) -> c.append(((Byte) v).byteValue())),
                Map.entry(Boolean.class, (c, v) -> c.append(((Boolean) v).booleanValue())),
                Map.entry(Character.class, (c, v) -> c.append(((Character) v).charValue())),
                Map.entry(Class.class, (c, v) -> {
                    if (c.append("class ")) c.append(((Class<?>) v).getName());
                }));
        verifyNoCaptureRendererCandidates(renderers);
        return renderers;
    }

    private static void verifyNoCaptureRendererCandidates(Map<Class<?>, NonSealedTypeRenderer> renderers) {
        for (Map.Entry<Class<?>, NonSealedTypeRenderer> entry : renderers.entrySet()) {
            NonSealedTypeRenderer renderer = entry.getValue();
            for (var field : renderer.getClass().getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    throw new IllegalStateException("Captured renderer lambda is not allowed for: " + entry.getKey());
                }
            }
        }
    }

    private static TypeRenderer resolveRenderer(Class<?> type) {
        TypeRenderer exact = EXACT_RENDERERS.get(type);
        if (exact != null) {
            return exact;
        }
        if (Loggable.class.isAssignableFrom(type)) {
            return LoggableRenderer.INSTANCE;
        }
        if (type.isArray()) {
            return ObjectArrayRenderer.INSTANCE;
        }
        if (Collection.class.isAssignableFrom(type)) {
            return CollectionRenderer.INSTANCE;
        }
        if (Map.class.isAssignableFrom(type)) {
            return MapRenderer.INSTANCE;
        }
        if (Enum.class.isAssignableFrom(type)) {
            return ENUM_RENDERER;
        }
        if (CharSequence.class.isAssignableFrom(type)) {
            return CHAR_SEQUENCE_RENDERER;
        }
        if (Number.class.isAssignableFrom(type)
                || LogRuntime.globalConfigBinding().isWhitelisted(type)) {
            return NUMBER_OR_WHITELIST_RENDERER;
        }
        return IdentityRenderer.INSTANCE;
    }
}
