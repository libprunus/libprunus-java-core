package org.libprunus.core.log.runtime;

import java.util.Objects;

public final class StringBuilderPool {

    static final int MAX_POOL_DEPTH = 8;
    static final int MAX_CAPACITY = 8192;
    static final int INITIAL_CAPACITY = 512;

    static final class PoolState {
        int cursor = 0;
        final StringBuilder[] items = new StringBuilder[MAX_POOL_DEPTH];
    }

    private static final ThreadLocal<PoolState> POOL = ThreadLocal.withInitial(PoolState::new);

    public static StringBuilder acquire() {
        if (Thread.currentThread().isVirtual()) {
            return new StringBuilder(INITIAL_CAPACITY);
        }
        PoolState state = POOL.get();
        StringBuilder sb;
        if (state.cursor > 0) {
            int top = --state.cursor;
            sb = state.items[top];
            state.items[top] = null;
        } else {
            sb = new StringBuilder(INITIAL_CAPACITY);
        }
        return sb;
    }

    public static void release(StringBuilder sb) {
        if (Thread.currentThread().isVirtual()) {
            return;
        }
        Objects.requireNonNull(sb, "Cannot release null StringBuilder");
        PoolState state = POOL.get();
        sb.setLength(0);
        if (sb.capacity() > MAX_CAPACITY) {
            return;
        }
        if (state.cursor < MAX_POOL_DEPTH) {
            for (int i = 0; i < state.cursor; i++) {
                if (state.items[i] == sb) {
                    return;
                }
            }
            state.items[state.cursor++] = sb;
        }
    }

    private StringBuilderPool() {
        throw new UnsupportedOperationException();
    }
}
