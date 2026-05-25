package org.libprunus.core.log.runtime;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Subprocess probe that drives multiple threads into {@link LogRuntime#initializeBinding} at the
 * same instant and reports the post-race winner/loser distribution. The "once-only" guard inside
 * {@code initializeBinding} is JVM-global state — verifying its concurrent behavior in a unit test
 * would require resetting that state mid-test and would race with the very contract being
 * verified, so the verification belongs to a fresh JVM.
 *
 * <p>Output contract on stdout:
 * <ul>
 *   <li>{@code WINNERS=<int>} — number of threads whose {@code initializeBinding} returned normally.
 *   <li>{@code ISE_COUNT=<int>} — number of threads whose {@code initializeBinding} threw
 *       {@link IllegalStateException} ("LogRuntime binding has already been initialized").
 *   <li>{@code OTHER_THROWABLES=<int>} — number of threads observing any other throwable (must be 0).
 *   <li>{@code BINDING_MAX_LENGTH=<int>} — the published max length, must equal {@code 4096}
 *       (the unique value all probe-supplied bindings carry).
 *   <li>{@code BINDING_IS_DEFAULT=false} — the binding is NOT the DEFAULT fallback, proving that
 *       one of the concurrent calls did succeed.
 * </ul>
 */
public final class LogRuntimeConcurrentInitProbe {

    private LogRuntimeConcurrentInitProbe() {
        throw new UnsupportedOperationException();
    }

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 16;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger winners = new AtomicInteger();
        AtomicInteger iseCount = new AtomicInteger();
        AtomicInteger otherThrowables = new AtomicInteger();

        AbstractLogConfig probeBinding = new AbstractLogConfig() {
            @Override
            public int getMaxMessageLength() {
                return 4096;
            }

            @Override
            public boolean isWhitelisted(Class<?> type) {
                return type == Integer.class;
            }
        };

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(
                    () -> {
                        try {
                            startLatch.await();
                            try {
                                LogRuntime.initializeBinding(probeBinding);
                                winners.incrementAndGet();
                            } catch (IllegalStateException ise) {
                                if ("LogRuntime binding has already been initialized".equals(ise.getMessage())) {
                                    iseCount.incrementAndGet();
                                } else {
                                    otherThrowables.incrementAndGet();
                                }
                            } catch (Throwable _) {
                                otherThrowables.incrementAndGet();
                            }
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                            otherThrowables.incrementAndGet();
                        } finally {
                            doneLatch.countDown();
                        }
                    },
                    "init-racer-" + i);
            threads[i].start();
        }

        startLatch.countDown();
        doneLatch.await();

        System.out.println("WINNERS=" + winners.get());
        System.out.println("ISE_COUNT=" + iseCount.get());
        System.out.println("OTHER_THROWABLES=" + otherThrowables.get());
        System.out.println("BINDING_MAX_LENGTH=" + LogRuntime.getGlobalMaxMessageLength());
        System.out.println("BINDING_IS_DEFAULT=" + (LogRuntime.getGlobalMaxMessageLength() == 512));
    }
}
