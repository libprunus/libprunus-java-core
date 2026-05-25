package org.libprunus.core.log.runtime;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;

/**
 * Renders {@link Collection} values as {@code [e1, e2, ...]}.
 *
 * <p>Lists implementing {@link RandomAccess} use {@code get(int)} indexing; everything else
 * iterates via {@link Collection#iterator()}. The fast path absorbs the
 * {@link IndexOutOfBoundsException} that arises from concurrent {@link java.util.ArrayList}
 * shrink.
 *
 * <p>Exception bucketing inside the body:
 * <ul>
 *   <li>{@link ConcurrentModificationException} and {@link IndexOutOfBoundsException} →
 *       {@code "...[CME]"} via {@link StringBuilderWithContext#forceAppendAuditMarker}.
 *   <li>{@link Error} other than {@link StackOverflowError} is rethrown unchanged.
 *   <li>Every other {@link Throwable} (including {@link java.util.NoSuchElementException} and
 *       unchecked exceptions raised by user-supplied iterators) is recorded as
 *       {@code "...[<FQCN>]"} via {@link StringBuilderWithContext#appendThrowableFallback}.
 * </ul>
 *
 * <p>{@link StringBuilderWithContext#enterRenderDepth()} / {@link StringBuilderWithContext#exitRenderDepth()}
 * bracket the body; {@code exitRenderDepth} runs unconditionally via {@code finally}. The closing
 * {@code ']'} is silently dropped when the context is already truncated.
 */
final class CollectionRenderer implements TypeRenderer {

    static final CollectionRenderer INSTANCE = new CollectionRenderer();

    private CollectionRenderer() {}

    @Override
    public void render(StringBuilderWithContext context, Object value) {
        if (!context.append('[')) return;
        if (!context.enterRenderDepth()) return;
        try {
            Collection<?> collection = (Collection<?>) value;
            if (collection.isEmpty()) {
                context.append(']');
                return;
            }
            if (collection instanceof List<?> list && list instanceof RandomAccess) {
                renderRandomAccessList(context, list);
            } else {
                renderIterator(context, collection.iterator());
            }
        } catch (ConcurrentModificationException | IndexOutOfBoundsException _) {
            context.forceAppendAuditMarker(StringBuilderWithContext.CONCURRENT_MODIFICATION_MARKER);
            return;
        } catch (Throwable throwable) {
            StringBuilderWithContext.handleRenderError(context, throwable);
            return;
        } finally {
            context.exitRenderDepth();
        }
        context.append(']');
    }

    private static void renderRandomAccessList(StringBuilderWithContext context, List<?> list) {
        int size = list.size();
        if (size == 0 || !context.appendObjectTo(list.get(0))) return;
        for (int index = 1; index < size; index++) {
            if (!context.prependSeparator() || !context.appendObjectTo(list.get(index))) {
                break;
            }
        }
    }

    private static void renderIterator(StringBuilderWithContext context, Iterator<?> it) {
        if (!it.hasNext() || !context.appendObjectTo(it.next())) return;
        while (it.hasNext()) {
            if (!context.prependSeparator() || !context.appendObjectTo(it.next())) {
                break;
            }
        }
    }
}
