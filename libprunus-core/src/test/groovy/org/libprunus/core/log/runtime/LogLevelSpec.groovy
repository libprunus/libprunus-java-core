package org.libprunus.core.log.runtime

import org.slf4j.Logger
import spock.lang.Specification

class LogLevelSpec extends Specification {

    def "valueOf returns correct enum instance for valid uppercase names"() {
        expect:
        LogLevel.valueOf(name) == expected

        where:
        name    || expected
        "TRACE" || LogLevel.TRACE
        "INFO"  || LogLevel.INFO
    }

    def "valueOf throws IllegalArgumentException for unrecognised or case-mismatched names"() {
        when:
        LogLevel.valueOf(name)

        then:
        thrown(IllegalArgumentException)

        where:
        name << ["trace", "Info", "INVALID_LEVEL", ""]
    }

    def "valueOf throws NullPointerException for null name"() {
        when:
        LogLevel.valueOf(null)

        then:
        thrown(NullPointerException)
    }

    def "values returns all five levels in strict declaration order"() {
        expect:
        LogLevel.values() == [LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR] as LogLevel[]
    }

    def "dispatch calls 2-arg Logger method when throwable is present"() {
        given:
        def logger = Mock(Logger)

        when:
        level.dispatch(logger, message, throwable)

        then:
        1 * logger."${level.name().toLowerCase()}"(message, throwable)

        where:
        [level, message, throwable] << [
                LogLevel.values().toList(),
                ["Standard message", "", null, "A" * 10000],
                [new RuntimeException("test")]
        ].combinations()
    }

    def "dispatch calls 1-arg Logger method when throwable is null"() {
        given:
        def logger = Mock(Logger)

        when:
        level.dispatch(logger, message, null)

        then:
        1 * logger."${level.name().toLowerCase()}"(message)

        where:
        [level, message] << [
                LogLevel.values().toList(),
                ["Standard message", "", null, "A" * 10000]
        ].combinations()
    }

    def "dispatch throws NullPointerException when logger is null"() {
        when:
        level.dispatch(null, message, throwable)

        then:
        thrown(NullPointerException)

        where:
        [level, message, throwable] << [
                LogLevel.values().toList(),
                ["Standard message", "", null, "A" * 10000],
                [new RuntimeException("test"), null]
        ].combinations()
    }

    def "isEnabled delegates to the corresponding logger probe method and returns its result"() {
        given:
        def logger = Mock(Logger)
        def methodName = "is${level.name().toLowerCase().capitalize()}Enabled"

        when:
        def result = level.isEnabled(logger)

        then:
        result == enabled
        1 * logger."$methodName"() >> enabled

        where:
        [level, enabled] << [LogLevel.values().toList(), [true, false]].combinations()
    }

    def "isEnabled should throw NullPointerException when invoked with a null logger reference"() {
        when:
        level.isEnabled(null)

        then:
        thrown(NullPointerException)
        0 * _

        where:
        level << LogLevel.values().toList()
    }

}
