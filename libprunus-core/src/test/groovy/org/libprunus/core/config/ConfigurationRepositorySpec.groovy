package org.libprunus.core.config

import spock.lang.Specification
import spock.lang.Subject

class ConfigurationRepositorySpec extends Specification {

    def "constructor stores initial config as current snapshot"() {
        given: "a valid initial config"
        def initial = new CoreRuntimeConfig()

        when: "the repository is constructed"
        @Subject ConfigurationRepository repo = new ConfigurationRepository(initial)

        then: "the snapshot is the same reference as the initial config"
        repo.getGlobalSnapshot().is(initial)
    }

    def "getGlobalSnapshot returns the stored snapshot"() {
        given: "a repository initialized with a known config"
        def initial = new CoreRuntimeConfig()

        @Subject ConfigurationRepository repo = new ConfigurationRepository(initial)

        when: "the global snapshot is retrieved"
        def result = repo.getGlobalSnapshot()

        then: "the returned value is the original config reference"
        result.is(initial)
    }

    def "refresh updates the current snapshot to the new config"() {
        given: "an initialized repository and a new config"
        @Subject ConfigurationRepository repo = new ConfigurationRepository(new CoreRuntimeConfig())
        def newConfig = new CoreRuntimeConfig()

        when: "the repository is refreshed with the new config"
        repo.refresh(newConfig)

        then: "the snapshot is the same reference as the new config"
        repo.getGlobalSnapshot().is(newConfig)
    }

    def "successive refreshes track the latest config"() {
        given: "an initialized repository"
        @Subject ConfigurationRepository repo = new ConfigurationRepository(new CoreRuntimeConfig())
        def lastConfig = null

        when: "the repository is refreshed multiple times"
        (1..refreshCount).each {
            lastConfig = new CoreRuntimeConfig()
            repo.refresh(lastConfig)
        }

        then: "the snapshot reflects the last config passed"
        repo.getGlobalSnapshot().is(lastConfig)

        where:
        refreshCount << [1, 2, 5]
    }
}
