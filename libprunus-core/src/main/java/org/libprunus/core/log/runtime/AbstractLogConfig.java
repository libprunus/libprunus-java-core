package org.libprunus.core.log.runtime;

import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.UUID;

public abstract class AbstractLogConfig {

    public static final AbstractLogConfig DEFAULT = new AbstractLogConfig() {
        @Override
        public int getMaxObjectLength() {
            return 512;
        }

        @Override
        public int getMaxObjectDepth() {
            return 5;
        }

        @Override
        public boolean isWhitelisted(Class<?> type) {
            return type != null
                    && (Enum.class.isAssignableFrom(type)
                            || Number.class.isAssignableFrom(type)
                            || CharSequence.class.isAssignableFrom(type)
                            || TemporalAccessor.class.isAssignableFrom(type)
                            || type == Boolean.class
                            || type == Character.class
                            || type == UUID.class
                            || Date.class.isAssignableFrom(type)
                            || type == Class.class);
        }
    };

    protected AbstractLogConfig() {}

    public abstract int getMaxObjectLength();

    public abstract int getMaxObjectDepth();

    public abstract boolean isWhitelisted(Class<?> type);
}
