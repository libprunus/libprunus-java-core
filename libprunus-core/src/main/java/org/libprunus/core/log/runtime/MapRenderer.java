package org.libprunus.core.log.runtime;

import java.util.ConcurrentModificationException;
import java.util.Map;

/**
 * Renders {@link Map} values as <code>{k1=v1, k2=v2, ...}</code>.
 *
 * <p>Each entry's key and value are dispatched through {@link StringBuilderWithContext#appendObjectTo}.
 *
 * <p>Exception bucketing inside the body:
 * <ul>
 *   <li>{@link ConcurrentModificationException} → {@code "...[CME]"} via
 *       {@link StringBuilderWithContext#forceAppendAuditMarker}. Map iteration cannot produce
 *       {@link IndexOutOfBoundsException} (no positional access), so it is not in the caught set.
 *   <li>{@link Error} other than {@link StackOverflowError} is rethrown unchanged.
 *   <li>Every other {@link Throwable} (including {@link java.util.NoSuchElementException},
 *       {@link ClassCastException} raised by the {@code (Map<?, ?>) value} cast inside the body,
 *       and unchecked exceptions raised by user-supplied iterators) is recorded as
 *       {@code "...[<FQCN>]"} via {@link StringBuilderWithContext#appendThrowableFallback}.
 * </ul>
 *
 * <p>{@link StringBuilderWithContext#enterRenderDepth()} / {@link StringBuilderWithContext#exitRenderDepth()}
 * bracket the body; {@code exitRenderDepth} runs unconditionally via {@code finally}. The closing
 * <code>'}'</code> is silently dropped when the context is already truncated.
 */
final class MapRenderer implements TypeRenderer {

    static final MapRenderer INSTANCE = new MapRenderer();

    private MapRenderer() {}

    @Override
    public void render(StringBuilderWithContext context, Object value) {
        if (!context.append('{')) return;
        if (!context.enterRenderDepth()) return;
        try {
            Map<?, ?> map = (Map<?, ?>) value;
            if (!map.isEmpty()) {
                renderMapEntries(context, map);
            }
        } catch (ConcurrentModificationException ignored) {
            context.forceAppendAuditMarker(StringBuilderWithContext.CONCURRENT_MODIFICATION_MARKER);
            return;
        } catch (Throwable throwable) {
            StringBuilderWithContext.handleRenderError(context, throwable);
            return;
        } finally {
            context.exitRenderDepth();
        }
        context.append('}');
    }

    private static void renderMapEntries(StringBuilderWithContext context, Map<?, ?> map) {
        var it = map.entrySet().iterator();
        if (!it.hasNext()) return;
        Map.Entry<?, ?> entry = it.next();
        if (!context.appendObjectTo(entry.getKey())
                || !context.append('=')
                || !context.appendObjectTo(entry.getValue())) {
            return;
        }
        while (it.hasNext()) {
            if (!context.prependSeparator()) return;
            entry = it.next();
            if (!context.appendObjectTo(entry.getKey())
                    || !context.append('=')
                    || !context.appendObjectTo(entry.getValue())) {
                break;
            }
        }
    }
}
