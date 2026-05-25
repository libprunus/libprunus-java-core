package org.libprunus.core.config

import org.libprunus.core.log.runtime.AbstractLogConfig
import org.libprunus.core.log.runtime.LogRuntime
import org.libprunus.core.log.runtime.LogRuntimeConfig
import org.libprunus.core.log.runtime.LogRuntimeTestSupport
import spock.lang.Specification

/**
 * End-to-end coverage for the data-plane wiring chain documented in
 * docs/usage/data-plane-wiring.md:
 *
 *   new ConfigurationRepository(initialConfig)  →  LogRuntime.isEnabled() reflects initial
 *   repository.refresh(newConfig)               →  LogRuntime.isEnabled() reflects new state
 *
 * Every observable surface is asserted on for every transition, on a complete matrix:
 *
 *   (subject side)               (global side)               (public surface)
 *   repository.getGlobalSnapshot()   LogRuntime.ACTIVE_CONFIG_REF[.get()]   LogRuntime.isEnabled()
 *
 *   For each side, both directions are closed:
 *     positive — resolves by whole-graph identity AND by value to the just-installed graph
 *     negative — does NOT resolve (by identity) to ANY previously-installed graph in the test
 *
 *   On top of that:
 *     - The compile-time binding (LogRuntime.boundConfig + getGlobalMaxMessageLength) is
 *       asserted unchanged after every step — proving the data plane never bleeds into the
 *       compile-time axis.
 *     - The displaced old AtomicReference (the one replaced by linkToDataPlane on construction)
 *       is asserted to retain its original content — proving linkToDataPlane is a pure-replace,
 *       never write-through into the ref it displaces.
 *
 * Cross-surface alignment is asserted by whole-graph identity (.is()) on both CoreRuntimeConfig
 * and the nested LogRuntimeConfig: any field added to either record in the future is covered
 * automatically because the same instance carries all of its fields by definition.
 */
class ConfigurationRepositoryDataPlaneIntegrationSpec extends Specification {

    def setup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def cleanup() {
        LogRuntimeTestSupport.resetBinding()
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

    def "construction aligns repo snapshot, data-plane ref, and isEnabled() to the initial graph by identity for enabled=#enabled"() {
        given: "initial graph and pre-construction baselines"
        def initialLog = new LogRuntimeConfig(enabled)
        def initial = new CoreRuntimeConfig(initialLog)
        def refBefore = LogRuntime.ACTIVE_CONFIG_REF
        def oldRefContent = refBefore.get()
        def oldRefLog = oldRefContent.log()
        and: "compile-time baseline pinned to an identifiable non-DEFAULT sentinel so that the post-construction untouched assertions have a real failure mode"
        def sentinelBound = installNonDefaultBoundConfigSentinel(321)
        def boundConfigBefore = LogRuntime.boundConfig
        def boundMaxLenBefore = LogRuntime.getGlobalMaxMessageLength()
        assert boundConfigBefore.is(sentinelBound)
        assert boundMaxLenBefore == 321

        when: "constructing repository"
        def repository = new ConfigurationRepository(initial)

        then: "repo snapshot alignment"
        repository.getGlobalSnapshot().is(initial)
        repository.getGlobalSnapshot().log().is(initialLog)
        repository.getGlobalSnapshot().log().enabled() == enabled

        and: "data-plane re-linked"
        !LogRuntime.ACTIVE_CONFIG_REF.is(refBefore)

        and: "data-plane reference alignment"
        LogRuntime.ACTIVE_CONFIG_REF.get().is(initial)
        LogRuntime.ACTIVE_CONFIG_REF.get().is(repository.getGlobalSnapshot())
        LogRuntime.ACTIVE_CONFIG_REF.get().log().is(initialLog)
        LogRuntime.ACTIVE_CONFIG_REF.get().log().enabled() == enabled

        and: "displaced ref isolation"
        refBefore.get().is(oldRefContent)
        refBefore.get().log().is(oldRefLog)

        and: "public API surface alignment"
        LogRuntime.isEnabled() == enabled

        and: "compile-time binding untouched"
        LogRuntime.boundConfig.is(boundConfigBefore)
        LogRuntime.getGlobalMaxMessageLength() == boundMaxLenBefore

        where:
        enabled << [true, false]
    }

    def "refresh writes through the linked AtomicReference on every transition without latching across a disabled-enabled-disabled round trip"() {
        given: "disabled repository, linked ref, baselines"
        def initialLog = new LogRuntimeConfig(false)
        def initial = new CoreRuntimeConfig(initialLog)
        def repository = new ConfigurationRepository(initial)
        def linkedRef = LogRuntime.ACTIVE_CONFIG_REF
        and: "compile-time baseline pinned to an identifiable non-DEFAULT sentinel so that the per-step untouched assertions have a real failure mode"
        def sentinelBound = installNonDefaultBoundConfigSentinel(777)
        def boundConfigBefore = LogRuntime.boundConfig
        def boundMaxLenBefore = LogRuntime.getGlobalMaxMessageLength()
        assert boundConfigBefore.is(sentinelBound)
        assert boundMaxLenBefore == 777

        expect: "initial preconditions on all surfaces"
        repository.getGlobalSnapshot().is(initial)
        LogRuntime.ACTIVE_CONFIG_REF.is(linkedRef)
        LogRuntime.ACTIVE_CONFIG_REF.get().is(initial)
        LogRuntime.ACTIVE_CONFIG_REF.get().log().is(initialLog)
        !LogRuntime.ACTIVE_CONFIG_REF.get().log().enabled()
        !LogRuntime.isEnabled()
        LogRuntime.boundConfig.is(boundConfigBefore)
        LogRuntime.getGlobalMaxMessageLength() == boundMaxLenBefore

        when: "refresh to enabled"
        def enabledLog = new LogRuntimeConfig(true)
        def enabledConfig = new CoreRuntimeConfig(enabledLog)
        repository.refresh(enabledConfig)

        then: "repo snapshot migrated to enabled"
        repository.getGlobalSnapshot().is(enabledConfig)
        repository.getGlobalSnapshot().log().is(enabledLog)
        repository.getGlobalSnapshot().log().enabled()

        and: "initial unreachable from repo"
        !repository.getGlobalSnapshot().is(initial)
        !repository.getGlobalSnapshot().log().is(initialLog)

        and: "data-plane writes through, no re-link"
        LogRuntime.ACTIVE_CONFIG_REF.is(linkedRef)
        LogRuntime.ACTIVE_CONFIG_REF.get().is(enabledConfig)
        LogRuntime.ACTIVE_CONFIG_REF.get().is(repository.getGlobalSnapshot())
        LogRuntime.ACTIVE_CONFIG_REF.get().log().is(enabledLog)
        LogRuntime.ACTIVE_CONFIG_REF.get().log().enabled()

        and: "initial unreachable from data plane"
        !LogRuntime.ACTIVE_CONFIG_REF.get().is(initial)
        !LogRuntime.ACTIVE_CONFIG_REF.get().log().is(initialLog)

        and: "public API flips on"
        LogRuntime.isEnabled()

        and: "compile-time binding untouched"
        LogRuntime.boundConfig.is(boundConfigBefore)
        LogRuntime.getGlobalMaxMessageLength() == boundMaxLenBefore

        when: "refresh to new disabled"
        def reDisabledLog = new LogRuntimeConfig(false)
        def reDisabled = new CoreRuntimeConfig(reDisabledLog)
        repository.refresh(reDisabled)

        then: "repo snapshot migrated, not latched"
        repository.getGlobalSnapshot().is(reDisabled)
        repository.getGlobalSnapshot().log().is(reDisabledLog)
        !repository.getGlobalSnapshot().log().enabled()

        and: "prior graphs unreachable from repo"
        !repository.getGlobalSnapshot().is(initial)
        !repository.getGlobalSnapshot().is(enabledConfig)
        !repository.getGlobalSnapshot().log().is(initialLog)
        !repository.getGlobalSnapshot().log().is(enabledLog)

        and: "data-plane migrated, still write-through"
        LogRuntime.ACTIVE_CONFIG_REF.is(linkedRef)
        LogRuntime.ACTIVE_CONFIG_REF.get().is(reDisabled)
        LogRuntime.ACTIVE_CONFIG_REF.get().is(repository.getGlobalSnapshot())
        LogRuntime.ACTIVE_CONFIG_REF.get().log().is(reDisabledLog)
        !LogRuntime.ACTIVE_CONFIG_REF.get().log().enabled()

        and: "prior graphs unreachable from data plane"
        !LogRuntime.ACTIVE_CONFIG_REF.get().is(initial)
        !LogRuntime.ACTIVE_CONFIG_REF.get().is(enabledConfig)
        !LogRuntime.ACTIVE_CONFIG_REF.get().log().is(initialLog)
        !LogRuntime.ACTIVE_CONFIG_REF.get().log().is(enabledLog)

        and: "public API flips back off"
        !LogRuntime.isEnabled()

        and: "compile-time binding untouched"
        LogRuntime.boundConfig.is(boundConfigBefore)
        LogRuntime.getGlobalMaxMessageLength() == boundMaxLenBefore
    }

    def "sequentially constructing two repositories causes the second to win the data plane and the first's snapshot to remain isolated from the new link"() {
        given: "first repository and its installed ref"
        def first = new ConfigurationRepository(new CoreRuntimeConfig(new LogRuntimeConfig(true)))
        def firstRef = LogRuntime.ACTIVE_CONFIG_REF

        when:
        def second = new ConfigurationRepository(new CoreRuntimeConfig(new LogRuntimeConfig(false)))

        then:
        !LogRuntime.ACTIVE_CONFIG_REF.is(firstRef)

        and:
        LogRuntime.ACTIVE_CONFIG_REF.is(second.@currentSnapshot)

        and:
        !LogRuntime.isEnabled()

        and:
        first.getGlobalSnapshot().log().enabled()

        and:
        firstRef.get().log().enabled()
    }
}
