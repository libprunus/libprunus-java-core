package org.libprunus.core.log.runtime;

import org.slf4j.Logger;

public enum LogLevel {
    TRACE {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isTraceEnabled();
        }

        @Override
        public void dispatch(Logger logger, String message, Throwable throwable) {
            if (throwable != null) {
                logger.trace(message, throwable);
            } else {
                logger.trace(message);
            }
        }
    },
    DEBUG {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isDebugEnabled();
        }

        @Override
        public void dispatch(Logger logger, String message, Throwable throwable) {
            if (throwable != null) {
                logger.debug(message, throwable);
            } else {
                logger.debug(message);
            }
        }
    },
    INFO {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isInfoEnabled();
        }

        @Override
        public void dispatch(Logger logger, String message, Throwable throwable) {
            if (throwable != null) {
                logger.info(message, throwable);
            } else {
                logger.info(message);
            }
        }
    },
    WARN {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isWarnEnabled();
        }

        @Override
        public void dispatch(Logger logger, String message, Throwable throwable) {
            if (throwable != null) {
                logger.warn(message, throwable);
            } else {
                logger.warn(message);
            }
        }
    },
    ERROR {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isErrorEnabled();
        }

        @Override
        public void dispatch(Logger logger, String message, Throwable throwable) {
            if (throwable != null) {
                logger.error(message, throwable);
            } else {
                logger.error(message);
            }
        }
    };

    public abstract boolean isEnabled(Logger logger);

    public abstract void dispatch(Logger logger, String message, Throwable throwable);
}
