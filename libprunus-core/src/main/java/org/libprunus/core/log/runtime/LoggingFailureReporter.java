package org.libprunus.core.log.runtime;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

final class LoggingFailureReporter {

    private static final int MAX_DETAILED_PER_SECOND = 10;
    private final long startNanos = System.nanoTime();
    private static final String SOE_OMITTED_SUFFIX = " [stack trace omitted due to StackOverflowError]";
    private static final String DROPPED_PREFIX = "libprunus: ";
    private static final String DROPPED_SUFFIX = " logging failure event(s) dropped (rate-limited)";

    private final AtomicLong rateLimiter = new AtomicLong(0L);
    private final LongAdder droppedCount = new LongAdder();
    private final AtomicLong lastReportedDropCount = new AtomicLong(0L);

    private static final LoggingFailureReporter INSTANCE = new LoggingFailureReporter();

    private LoggingFailureReporter() {}

    static LoggingFailureReporter instance() {
        return INSTANCE;
    }

    void offer(String ownerAndMethod, Throwable throwable) {
        if (throwable instanceof VirtualMachineError virtualMachineError
                && !(virtualMachineError instanceof StackOverflowError)) {
            throw virtualMachineError;
        }

        long currentNanos = System.nanoTime() - startNanos;
        long currentSecond = TimeUnit.NANOSECONDS.toSeconds(currentNanos);
        if (!tryAcquire(currentSecond)) {
            droppedCount.increment();
            return;
        }

        report(ownerAndMethod, throwable);
    }

    private boolean tryAcquire(long currentSecond) {
        while (true) {
            long current = rateLimiter.get();
            long storedSecond = current >>> 32;
            long count = current & 0xFFFFFFFFL;
            if (storedSecond == currentSecond) {
                if (count >= MAX_DETAILED_PER_SECOND) {
                    return false;
                }
                long next = (storedSecond << 32) | (count + 1L);
                if (rateLimiter.compareAndSet(current, next)) {
                    return true;
                }
            } else {
                if (currentSecond < storedSecond) {
                    return false;
                }
                long next = (currentSecond << 32) | 1L;
                if (rateLimiter.compareAndSet(current, next)) {
                    return true;
                }
            }
        }
    }

    private void report(String ownerAndMethod, Throwable throwable) {
        reportDroppedIfNeeded();

        try {
            PrintStream err = System.err;
            err.print("libprunus logging failure at ");
            if (throwable == null) {
                err.println(ownerAndMethod);
                return;
            }
            if (throwable instanceof StackOverflowError) {
                err.println(ownerAndMethod + SOE_OMITTED_SUFFIX);
                return;
            }
            err.println(ownerAndMethod);
            throwable.printStackTrace(err);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable _) {
            try {
                PrintStream err = System.err;
                err.print("libprunus logging failure and failed to report it: ");
                err.print(ownerAndMethod);
                err.print(System.lineSeparator());
            } catch (Throwable _) {
                // final fallback: if even System.err write threw, swallow to avoid logging-induced crash
            }
        }
    }

    private void reportDroppedIfNeeded() {
        while (true) {
            long currentDropped = droppedCount.sum();
            long lastReported = lastReportedDropCount.get();
            if (currentDropped <= lastReported) {
                return;
            }
            if (lastReportedDropCount.compareAndSet(lastReported, currentDropped)) {
                long newlyDropped = currentDropped - lastReported;
                try {
                    PrintStream err = System.err;
                    err.print(DROPPED_PREFIX);
                    err.print(newlyDropped);
                    err.print(DROPPED_SUFFIX);
                    err.print(System.lineSeparator());
                } catch (VirtualMachineError e) {
                    throw e;
                } catch (Throwable _) {
                    // dropped-count reporter: swallow secondary System.err failure (already inside best-effort path)
                }
                return;
            }
        }
    }
}
