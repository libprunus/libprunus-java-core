package org.libprunus.core.log.runtime;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.libprunus.core.config.CoreRuntimeConfig;

/**
 * Subprocess probe that drives a writer thread flipping {@link LogRuntime#linkToDataPlane}'d
 * config in tight loop while a reader thread polls {@link LogRuntime#isEnabled} and records
 * whether both polarities are eventually observed and whether any unexpected throwable surfaces.
 * Validates the documented hot-swap visibility contract on {@code LogRuntime.ACTIVE_CONFIG_REF}
 * (declared {@code volatile AtomicReference<CoreRuntimeConfig>}) without relying on JDK
 * AtomicReference behavior alone — the project-layered contract is that writes flow through to
 * reader threads on the next call without an explicit fence.
 *
 * <p>Output contract:
 * <ul>
 *   <li>{@code OBSERVED_ENABLED_TRUE=true|false}
 *   <li>{@code OBSERVED_ENABLED_FALSE=true|false}
 *   <li>{@code OBSERVED_NPE=true|false} — must be {@code false}; isEnabled() must never NPE on
 *       the {@code .log().enabled()} chain because the publisher always sets a non-null graph.
 *   <li>{@code OBSERVED_OTHER_THROWABLE=true|false} — must be {@code false}.
 * </ul>
 */
public final class LogRuntimeDataPlaneVisibilityProbe {

    private LogRuntimeDataPlaneVisibilityProbe() {
        throw new UnsupportedOperationException();
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<CoreRuntimeConfig> activeRef =
                new AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(true)));
        LogRuntime.linkToDataPlane(activeRef);

        int flipCount = 8192;
        CountDownLatch readerReady = new CountDownLatch(1);
        AtomicBoolean writerDone = new AtomicBoolean(false);
        AtomicBoolean observedTrue = new AtomicBoolean(false);
        AtomicBoolean observedFalse = new AtomicBoolean(false);
        AtomicBoolean observedNpe = new AtomicBoolean(false);
        AtomicBoolean observedOther = new AtomicBoolean(false);

        Thread reader = new Thread(
                () -> {
                    readerReady.countDown();
                    while (!writerDone.get()) {
                        try {
                            if (LogRuntime.isEnabled()) {
                                observedTrue.set(true);
                            } else {
                                observedFalse.set(true);
                            }
                        } catch (NullPointerException _) {
                            observedNpe.set(true);
                        } catch (Throwable _) {
                            observedOther.set(true);
                        }
                    }
                },
                "data-plane-reader");
        reader.setDaemon(true);
        reader.start();

        readerReady.await();
        int i = 0;
        while (i < flipCount || !observedTrue.get() || !observedFalse.get()) {
            activeRef.set(new CoreRuntimeConfig(new LogRuntimeConfig(i % 2 == 0)));
            i++;
        }
        writerDone.set(true);
        reader.join();

        System.out.println("OBSERVED_ENABLED_TRUE=" + observedTrue.get());
        System.out.println("OBSERVED_ENABLED_FALSE=" + observedFalse.get());
        System.out.println("OBSERVED_NPE=" + observedNpe.get());
        System.out.println("OBSERVED_OTHER_THROWABLE=" + observedOther.get());
    }
}
