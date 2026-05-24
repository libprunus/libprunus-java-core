package org.libprunus.core.log.runtime;

/**
 * Thread-local-pooled supplier of {@link StringBuilderWithContext}. Each platform thread owns its
 * own fixed-capacity cursor stack; virtual threads bypass pooling entirely.
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>{@link #acquire()} returns a reset-state context whose backing builder starts at
 *       {@code INITIAL_CAPACITY} (512 chars). If the per-thread pool has a slot, that instance is
 *       reused; otherwise a new one is allocated.
 *   <li>{@link #release(StringBuilderWithContext)} pushes the context onto the per-thread stack
 *       after {@code reset(0)} clears its state. {@code null} input is a no-op.
 *   <li>{@link #acquireWithPrefix(String)} is a convenience that calls {@link #acquire()} and
 *       writes {@code prefix} into the builder before returning.
 * </ul>
 *
 * <h2>Capacity gate</h2>
 * On release the implementation checks {@code builder.capacity() > max(8192,
 * LogRuntime.getGlobalMaxMessageLength() * 2)}. If true the instance is discarded — a one-off
 * oversized message cannot pin a large {@code char[]} in the pool.
 *
 * <h2>Virtual thread bypass</h2>
 * On virtual threads {@code acquire} always allocates a fresh instance and {@code release} is a
 * no-op. Pooling on threads that may exist for microseconds would cost more than it saves and
 * would inflate per-thread memory under high virtual-thread fan-out.
 *
 * <h2>Double-release de-duplication</h2>
 * Before pushing, {@code release} scans the stack and silently drops the second release if the
 * same context (or its backing {@link StringBuilder}) is already pooled. This makes the
 * failure-recovery code paths safe: both
 * {@link StringBuilderWithContext#recoverToStringFallback} and
 * {@link StringBuilderWithContext#handleRenderFailure} unconditionally call {@code release}, so
 * any accidental double release from defensive branches is absorbed rather than corrupting the
 * pool with a duplicate slot.
 *
 * <p>Cursor capacity is {@code MAX_POOL_DEPTH} = 8 per thread. Excess releases beyond that
 * capacity are also discarded.
 */
public final class StringBuilderPool {

    private static final int MAX_POOL_DEPTH = 8;
    private static final int DEFAULT_MAX_CAPACITY = 8192;
    private static final int INITIAL_CAPACITY = 512;

    private static final class PoolState {
        int cursor = 0;
        final StringBuilderWithContext[] items = new StringBuilderWithContext[MAX_POOL_DEPTH];
    }

    private static final ThreadLocal<PoolState> POOL = ThreadLocal.withInitial(PoolState::new);

    public static StringBuilderWithContext acquire() {
        int maxMessageLength = LogRuntime.getGlobalMaxMessageLength();
        if (Thread.currentThread().isVirtual()) {
            StringBuilderWithContext item = new StringBuilderWithContext(new StringBuilder(INITIAL_CAPACITY));
            item.reset(maxMessageLength);
            return item;
        }
        PoolState state = POOL.get();
        if (state.cursor > 0) {
            int top = --state.cursor;
            StringBuilderWithContext item = state.items[top];
            state.items[top] = null;
            item.reset(maxMessageLength);
            return item;
        }
        StringBuilderWithContext item = new StringBuilderWithContext(new StringBuilder(INITIAL_CAPACITY));
        item.reset(maxMessageLength);
        return item;
    }

    public static StringBuilderWithContext acquireWithPrefix(String prefix) {
        StringBuilderWithContext item = acquire();
        item.append(prefix);
        return item;
    }

    public static void release(StringBuilderWithContext item) {
        if (Thread.currentThread().isVirtual() || item == null) {
            return;
        }
        StringBuilder builder = item.builder;
        int dynamicMaxCapacity = Math.max(DEFAULT_MAX_CAPACITY, LogRuntime.getGlobalMaxMessageLength() * 2);
        if (builder.capacity() > dynamicMaxCapacity) {
            return;
        }
        PoolState state = POOL.get();
        item.reset(0);
        if (state.cursor < MAX_POOL_DEPTH) {
            for (int i = 0; i < state.cursor; i++) {
                StringBuilderWithContext pooled = state.items[i];
                if (pooled == item || pooled.builder == builder) {
                    return;
                }
            }
            state.items[state.cursor++] = item;
        }
    }

    private StringBuilderPool() {
        throw new UnsupportedOperationException();
    }
}
