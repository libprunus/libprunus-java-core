package org.libprunus.core.config

import java.util.concurrent.atomic.AtomicReference
import org.libprunus.core.log.runtime.AotLogRuntime
import org.libprunus.core.log.runtime.LogRuntimeConfig
import spock.lang.Specification

class ConfigurationRepositorySpec extends Specification {

    private static CoreRuntimeConfig config(boolean enabled = true) {
        new CoreRuntimeConfig(new LogRuntimeConfig(enabled))
    }

    def "getGlobalSnapshot returns current reference including forced null state"() {
        given: "a repository initialized with a valid snapshot"
        def initial = config(true)
        def repository = new ConfigurationRepository(initial)
        def reference = repository.@currentSnapshot

        when: "the internal snapshot reference is optionally forced to null"
        if (forceNullState) {
            reference.set(null)
        }

        then: "returned snapshot reflects current atomic state"
        if (forceNullState) {
            repository.getGlobalSnapshot() == null
        } else {
            repository.getGlobalSnapshot().is(initial)
        }

        where:
        forceNullState << [false, true]
    }

    def "initialize repository with valid configuration and link to data plane"() {
        given: "a valid CoreRuntimeConfig instance"
        def initialConfig = config(enabled)

        when: "the ConfigurationRepository is instantiated with the initial configuration"
        def repository = new ConfigurationRepository(initialConfig)

        then: "the internal snapshot is set, and it is linked to the AotLogRuntime data plane"
        repository.getGlobalSnapshot() == initialConfig
        def repositoryReference = repository.@currentSnapshot
        def runtimeReference = AotLogRuntime.@ACTIVE_CONFIG_REF
        runtimeReference.is(repositoryReference)
        runtimeReference.get() == initialConfig

        where:
        enabled << [true, false]
    }

    def "retrieve the current global configuration snapshot"() {
        given: "a ConfigurationRepository is already initialized with a specific CoreRuntimeConfig"
        def mockConfigA = config(true)
        def repository = new ConfigurationRepository(mockConfigA)

        when: "the getGlobalSnapshot method is invoked"
        def result = repository.getGlobalSnapshot()

        then: "the currently held CoreRuntimeConfig instance is returned"
        result == mockConfigA
    }

    def "refresh the repository with a non-null configuration snapshot"() {
        given: "a ConfigurationRepository exists with an old configuration"
        def oldConfig = config(true)
        def repository = new ConfigurationRepository(oldConfig)
        def newConfig = useSameReference ? oldConfig : config(false)

        when: "the refresh method is called with the new configuration"
        repository.refresh(newConfig)

        then: "the internal snapshot references the supplied non-null configuration"
        repository.getGlobalSnapshot().is(newConfig)

        where:
        useSameReference << [false, true]
    }

    def "fails to initialize repository with null configuration without triggering runtime link"() {
        when: "ConfigurationRepository is instantiated with null initial config"
        new ConfigurationRepository(null)

        then: "NullPointerException is thrown with correct message"
        def ex = thrown(NullPointerException)
        ex.message == "initialConfig must not be null"
    }

    def "fails to refresh repository with null configuration while preserving snapshot"() {
        given: "a repository initialized with valid configuration"
        def oldConfig = config(true)
        def repository = new ConfigurationRepository(oldConfig)

        when: "refresh is called with null new config"
        repository.refresh(null)

        then: "NullPointerException is thrown with correct message"
        def ex = thrown(NullPointerException)
        ex.message == "newConfig must not be null"

        and: "the global snapshot remains unchanged"
        repository.getGlobalSnapshot() == oldConfig

    }

}
