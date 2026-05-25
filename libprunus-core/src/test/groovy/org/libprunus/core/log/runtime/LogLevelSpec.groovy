package org.libprunus.core.log.runtime

import org.slf4j.Logger
import spock.lang.Specification

class LogLevelSpec extends Specification {

    def "values returns all six levels in strict declaration order"() {
        expect:
        LogLevel.values() == [LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR, LogLevel.OFF] as LogLevel[]
    }

    def "isEnabled delegates to the matching SLF4J predicate per level and returns its result"() {
        given:
        def logger = Mock(Logger)

        when:
        def result = level.isEnabled(logger)

        then:
        result == probeResult
        1 * logger."$predicate"() >> probeResult
        0 * logger._

        where:
        [level, predicate, probeResult] << [
                [
                        [LogLevel.TRACE, 'isTraceEnabled'],
                        [LogLevel.DEBUG, 'isDebugEnabled'],
                        [LogLevel.INFO,  'isInfoEnabled'],
                        [LogLevel.WARN,  'isWarnEnabled'],
                        [LogLevel.ERROR, 'isErrorEnabled'],
                ],
                [true, false],
        ].combinations().collect { pair, r -> [pair[0], pair[1], r] }
    }

    def "OFF isEnabled returns false without ever touching the logger argument across mock-and-null logger inputs"() {
        given:
        def mockLogger = Mock(Logger)

        when:
        def mockResult = LogLevel.OFF.isEnabled(mockLogger)
        def nullResult = LogLevel.OFF.isEnabled(null)

        then:
        !mockResult
        !nullResult
        noExceptionThrown()
        0 * mockLogger._
    }

}
