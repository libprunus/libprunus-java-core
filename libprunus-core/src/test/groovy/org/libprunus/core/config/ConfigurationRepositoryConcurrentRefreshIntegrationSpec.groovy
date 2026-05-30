package org.libprunus.core.config

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.libprunus.core.log.runtime.LogRuntime
import org.libprunus.core.log.runtime.LogRuntimeConfig
import org.libprunus.core.log.runtime.LogRuntimeTestSupport
import spock.lang.Specification

/**
 * Pins the cross-thread observability contract of {@link ConfigurationRepository} on top of
 * {@link LogRuntime}'s data plane: while a writer thread refreshes the global config at high
 * frequency with strictly boolean-valued payloads, any number of reader threads invoking
 * {@code LogRuntime.isEnabled()} must:
 *
 *   - never observe a {@code null} graph (atomic publication is total — the live
 *     {@link java.util.concurrent.atomic.AtomicReference} is never set to {@code null} and
 *     {@link LogRuntime#isEnabled} must never NPE on the {@code .log().enabled()} chain);
 *   - never observe a torn-out non-boolean value (the readers only ever see {@code true} or
 *     {@code false} — the only legal values the writer ever publishes).
 *
 * The "no missed update" stronger property is intentionally <em>not</em> asserted: that property
 * is owned by {@link java.util.concurrent.atomic.AtomicReference} itself (a JDK contract) and
 * falls under the Standard Library Scope Exclusion in docs/contributing/test.md Ch1 §6. What the
 * project owns — and what this spec pins — is the absence of NPE / value tearing under the
 * atomic-publish + synchronized-link pattern that the production code chose.
 */
class ConfigurationRepositoryConcurrentRefreshIntegrationSpec extends Specification {

    def setup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def cleanup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def "concurrent refresh and isEnabled reads never observe a null graph nor a non-boolean torn value"() {
        given: "repository, observers, and pool"
        def repository = new ConfigurationRepository(new CoreRuntimeConfig(new LogRuntimeConfig(true)))
        def repoRef = repository.@currentSnapshot
        def observedEnabled = new AtomicBoolean(false)
        def observedDisabled = new AtomicBoolean(false)
        def observedNullGraph = new AtomicBoolean(false)
        def sampledRefIsRepoRef = new AtomicBoolean(true)
        def unexpected = ConcurrentHashMap.newKeySet()
        def pool = Executors.newFixedThreadPool(9)
        def allReady = new CountDownLatch(9)
        def startGate = new CountDownLatch(1)

        when: "writer flips, readers sample"
        def writerDone = new AtomicBoolean(false)
        def writer = pool.submit({
            allReady.countDown()
            startGate.await()
            try {
                for (int i = 0; i < 4096; i++) {
                    repository.refresh(new CoreRuntimeConfig(new LogRuntimeConfig(i % 2 == 0)))
                }
            } finally {
                writerDone.set(true)
            }
        } as Runnable)
        def readers = (1..8).collect {
            pool.submit({
                allReady.countDown()
                startGate.await()
                while (!writerDone.get()) {
                    try {
                        def activeRef = LogRuntime.ACTIVE_CONFIG_REF
                        if (!activeRef.is(repoRef)) {
                            sampledRefIsRepoRef.set(false)
                        }
                        def graph = activeRef.get()
                        if (graph == null) {
                            observedNullGraph.set(true)
                            continue
                        }
                        boolean sampled = LogRuntime.isEnabled()
                        if (sampled) {
                            observedEnabled.set(true)
                        } else {
                            observedDisabled.set(true)
                        }
                    } catch (Throwable t) {
                        unexpected.add(t.getClass().getName() + ":" + t.getMessage())
                    }
                }
            } as Runnable)
        }
        assert allReady.await(10, TimeUnit.SECONDS), "worker threads failed to reach start gate"
        startGate.countDown()
        writer.get(30, TimeUnit.SECONDS)
        readers*.get(30, TimeUnit.SECONDS)

        then: "no reader throwables"
        unexpected.isEmpty()

        and: "global ref and repo ref are the same AtomicReference at every sample — collapses the would-be observedNullSnapshot witness into the observedNullGraph one"
        sampledRefIsRepoRef.get()

        and: "no null graph observed"
        !observedNullGraph.get()

        and: "both boolean polarities observed"
        observedEnabled.get()
        observedDisabled.get()

        and: "post-burst final state"
        LogRuntime.ACTIVE_CONFIG_REF.is(repoRef)
        !LogRuntime.isEnabled()

        cleanup: "pool teardown"
        pool?.shutdownNow()
        pool?.awaitTermination(5, TimeUnit.SECONDS)
    }
}
