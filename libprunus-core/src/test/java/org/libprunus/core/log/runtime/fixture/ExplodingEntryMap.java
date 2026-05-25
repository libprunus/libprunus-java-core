package org.libprunus.core.log.runtime.fixture;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A test-only {@link Map} whose single entry's {@link Map.Entry#getKey()} or
 * {@link Map.Entry#getValue()} throws a configured {@link RuntimeException}.
 *
 * <p>Implemented purely in Java with named inner classes — Groovy anonymous {@link Map.Entry}
 * subclasses must be avoided here because Groovy's MOP can cause runaway recursion in fixture
 * accessor methods, producing a real {@link StackOverflowError} instead of the configured
 * throwable.
 */
public final class ExplodingEntryMap extends AbstractMap<Object, Object> {

    private final boolean failOnKey;
    private final RuntimeException error;

    public ExplodingEntryMap(boolean failOnKey, RuntimeException error) {
        this.failOnKey = failOnKey;
        this.error = error;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return new ExplodingEntrySet();
    }

    private final class ExplodingEntrySet extends AbstractSet<Map.Entry<Object, Object>> {

        @Override
        public Iterator<Map.Entry<Object, Object>> iterator() {
            return new ExplodingEntryIterator();
        }

        @Override
        public int size() {
            return 1;
        }
    }

    private final class ExplodingEntryIterator implements Iterator<Map.Entry<Object, Object>> {

        private int emitted = 0;

        @Override
        public boolean hasNext() {
            return emitted < 1;
        }

        @Override
        public Map.Entry<Object, Object> next() {
            emitted++;
            return new ExplodingEntry();
        }
    }

    private final class ExplodingEntry implements Map.Entry<Object, Object> {

        @Override
        public Object getKey() {
            if (failOnKey) {
                throw error;
            }
            return "k";
        }

        @Override
        public Object getValue() {
            if (!failOnKey) {
                throw error;
            }
            return "v";
        }

        @Override
        public Object setValue(Object value) {
            throw new UnsupportedOperationException();
        }
    }
}
