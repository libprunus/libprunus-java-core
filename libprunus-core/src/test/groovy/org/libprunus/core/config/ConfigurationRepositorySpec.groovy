package org.libprunus.core.config

import org.libprunus.core.log.runtime.AbstractLogConfig
import org.libprunus.core.log.runtime.LogRuntime
import org.libprunus.core.log.runtime.LogRuntimeConfig
import org.libprunus.core.log.runtime.LogRuntimeTestSupport
import spock.lang.Specification

class ConfigurationRepositorySpec extends Specification {

    def setup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def cleanup() {
        LogRuntimeTestSupport.resetBinding()
    }

    private static CoreRuntimeConfig config(boolean enabled = true) {
        new CoreRuntimeConfig(new LogRuntimeConfig(enabled))
    }

    /**
     * Installs an identifiable non-DEFAULT sentinel into the compile-time binding axis so that the
     * subsequent "compile-time binding untouched" assertions have a real failure mode. Without
     * this, the fixture's resetBinding() leaves boundConfig == AbstractLogConfig.DEFAULT and the
     * captured baseline collapses to "DEFAULT == DEFAULT" — a tautology that would silently pass
     * even if the production code accidentally reassigned boundConfig to a different DEFAULT-shaped
     * instance.
     */
    private static AbstractLogConfig installNonDefaultBoundConfigSentinel(int maxMessageLength) {
        def sentinel = new AbstractLogConfig() {
            @Override
            int getMaxMessageLength() { maxMessageLength }

            @Override
            boolean isWhitelisted(Class<?> type) { false }
        }
        LogRuntime.boundConfig = sentinel
        LogRuntime.boundMaxMessageLength = maxMessageLength
        sentinel
    }

    def "ConfigurationRepository initializes snapshot and links runtime data plane"() {
        given: "initial config"
        def initialConfig = config(enabled)
        and: "compile-time baseline pinned to an identifiable non-DEFAULT sentinel"
        // The fixture's resetBinding() leaves the compile-time axis at AbstractLogConfig.DEFAULT;
        // installing a non-DEFAULT sentinel here gives the post-construction "untouched"
        // assertions below a real failure mode (otherwise both sides collapse to DEFAULT).
        def sentinelBound = installNonDefaultBoundConfigSentinel(123)
        def boundConfigBefore = LogRuntime.boundConfig
        def boundMaxLenBefore = LogRuntime.getGlobalMaxMessageLength()
        assert boundConfigBefore.is(sentinelBound)
        assert boundMaxLenBefore == 123

        when:
        def repository = new ConfigurationRepository(initialConfig)

        then: "snapshot is the very instance — no defensive copy"
        repository.getGlobalSnapshot().is(initialConfig)

        and: "data plane forwards by reference to the same instance"
        LogRuntime.ACTIVE_CONFIG_REF.get().is(initialConfig)

        and:
        // Symmetric absence-of-side-effect closure on the unrelated compile-time axis:
        // construction must not perturb boundConfig or boundMaxMessageLength.
        LogRuntime.boundConfig.is(boundConfigBefore)
        LogRuntime.getGlobalMaxMessageLength() == boundMaxLenBefore

        where:
        enabled << [true, false]
    }

    def "ConfigurationRepository constructor rejects invalid initial config without touching the runtime data plane"() {
        given: "sentinel installed into data plane"
        // Explicit sentinel installation eliminates the false-positive risk of being silently
        // satisfied by whatever default value resetBinding() happens to leave behind.
        def sentinelConfig = new CoreRuntimeConfig(new LogRuntimeConfig(false))
        def linkedRefBefore = LogRuntime.ACTIVE_CONFIG_REF
        linkedRefBefore.set(sentinelConfig)
        assert linkedRefBefore.get().is(sentinelConfig)

        when:
        new ConfigurationRepository(null)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "initialConfig must not be null"

        and:
        // Proves the failed construction did not call linkToDataPlane and therefore did not
        // publish any partial state — the AtomicReference field instance itself is unchanged.
        def linkedRefAfter = LogRuntime.ACTIVE_CONFIG_REF
        linkedRefAfter.is(linkedRefBefore)

        and:
        // No value-level mutation reached the data plane either: still the exact sentinel,
        // not the resetBinding default, not a half-built CoreRuntimeConfig, not null.
        linkedRefAfter.get().is(sentinelConfig)
    }

    def "constructor installs a fresh AtomicReference into LogRuntime rather than reusing a pre-existing one"() {
        given:
        def preExistingRef = LogRuntime.ACTIVE_CONFIG_REF

        when:
        def repository = new ConfigurationRepository(config(true))

        then: "the data-plane link has been replaced — construction did not piggyback"
        !LogRuntime.ACTIVE_CONFIG_REF.is(preExistingRef)

        and: "the published link is the repository's own currentSnapshot field instance"
        LogRuntime.ACTIVE_CONFIG_REF.is(repository.@currentSnapshot)

        and: "the repository's own currentSnapshot field is itself a newly constructed AtomicReference — not the pre-existing global one"
        !repository.@currentSnapshot.is(preExistingRef)
    }

    def "getGlobalSnapshot returns the currently held snapshot without mutating internal or global state"() {
        given:
        def initial = config(true)
        def repository = new ConfigurationRepository(initial)

        when:
        def result = repository.getGlobalSnapshot()

        then: "exact instance returned by identity"
        result.is(initial)

        and:
        // Non-destructive read: a second probe of the internal currentSnapshot AtomicReference
        // still resolves to the same instance, ruling out a hypothetical getAndSet/getAndUpdate
        // refactor that would have wiped the holder on read.
        repository.@currentSnapshot.get().is(initial)

        and: "global data-plane link is unaffected by the read"
        LogRuntime.ACTIVE_CONFIG_REF.get().is(initial)
    }

    def "refresh propagates the new configuration to both snapshot and runtime data plane regardless of identity overlap with the old config"() {
        given: "repository plus captured link"
        def initialConfig = config(true)
        def repository = new ConfigurationRepository(initialConfig)
        def linkedRefBefore = LogRuntime.ACTIVE_CONFIG_REF
        // useSameReference=true probes that refresh accepts the currently-held config instance
        // without short-circuiting — refresh must still walk the AtomicReference.set() path even
        // when newConfig is identity-equal to the previously published value. A future
        // `if (newConfig == current) return;` short-circuit would not change observable .is()
        // identity but would invalidate the write-through contract — kept here so the
        // disabled→same-instance-true scenario is co-located with the value-changing scenario.
        def newConfig = useSameReference ? initialConfig : config(false)

        when:
        repository.refresh(newConfig)

        then: "local snapshot resolves to the input by identity"
        repository.getGlobalSnapshot().is(newConfig)

        and: "data plane resolves to the input by identity — refresh writes through the same link"
        LogRuntime.ACTIVE_CONFIG_REF.get().is(newConfig)

        and:
        // Refresh must update the value inside the existing link rather than call linkToDataPlane
        // and replace the link — assert the AtomicReference field instance is unchanged, both at
        // the data plane and on the repository's own currentSnapshot field.
        def linkedRefAfter = LogRuntime.ACTIVE_CONFIG_REF
        linkedRefAfter.is(linkedRefBefore)
        repository.@currentSnapshot.is(linkedRefBefore)

        where:
        useSameReference << [false, true]
    }

    def "refresh rejects invalid config and leaves both snapshot and runtime data plane unchanged"() {
        given: "repository seeded with identifiable sentinel oldConfig; baseline captured"
        // Pre-condition assert eliminates any false-positive risk from environmental drift —
        // the sentinel must actually be the published value before we attempt the failing refresh.
        def oldConfig = config(true)
        def repository = new ConfigurationRepository(oldConfig)
        def linkedRefBefore = LogRuntime.ACTIVE_CONFIG_REF
        def runtimeSnapshotBefore = linkedRefBefore.get()
        assert runtimeSnapshotBefore.is(oldConfig)

        when:
        repository.refresh(null)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "newConfig must not be null"

        and: "local snapshot still resolves to the original old config by identity"
        repository.getGlobalSnapshot().is(oldConfig)

        and:
        // Proves the failed refresh did not detour through linkToDataPlane — the
        // AtomicReference field instance is unchanged.
        def linkedRefAfter = LogRuntime.ACTIVE_CONFIG_REF
        linkedRefAfter.is(linkedRefBefore)

        and: "value inside the unchanged AtomicReference is still the pre-refresh snapshot — no partial publication"
        linkedRefAfter.get().is(runtimeSnapshotBefore)

        and: "repository's own currentSnapshot field reference is itself unchanged — no internal field swap occurred during the failed refresh"
        repository.@currentSnapshot.is(linkedRefBefore)
    }

    def "refresh applied twice in succession leaves only the latest config reachable by identity from both snapshot and data plane"() {
        given:
        def repository = new ConfigurationRepository(config(true))
        def first = config(true)
        def second = config(false)

        when:
        repository.refresh(first)
        repository.refresh(second)

        then: "latest config wins on both surfaces"
        repository.getGlobalSnapshot().is(second)
        LogRuntime.ACTIVE_CONFIG_REF.get().is(second)

        and: "the earlier-refreshed instance has lost identity from both surfaces — no latch, no cache"
        !repository.getGlobalSnapshot().is(first)
        !LogRuntime.ACTIVE_CONFIG_REF.get().is(first)
    }

}
