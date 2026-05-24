package org.libprunus.core.log.runtime;

import org.slf4j.Logger;

/**
 * Per-callsite logging level enum delegating to SLF4J's {@link Logger} predicates.
 */
public enum LogLevel {
    TRACE {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isTraceEnabled();
        }
    },
    DEBUG {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isDebugEnabled();
        }
    },
    INFO {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isInfoEnabled();
        }
    },
    WARN {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isWarnEnabled();
        }
    },
    ERROR {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isErrorEnabled();
        }
    },
    OFF {
        @Override
        public boolean isEnabled(Logger logger) {
            return false;
        }
    };

    public abstract boolean isEnabled(Logger logger);
}
