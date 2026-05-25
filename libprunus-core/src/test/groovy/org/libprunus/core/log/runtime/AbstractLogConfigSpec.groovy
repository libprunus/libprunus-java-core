package org.libprunus.core.log.runtime

import spock.lang.Shared
import spock.lang.Specification

class AbstractLogConfigSpec extends Specification {

    @Shared
    AbstractLogConfig config = AbstractLogConfig.DEFAULT

    def "DEFAULT retrieves the default maximum allowed message length constraint"() {
        when:
        def maxLength = config.getMaxMessageLength()

        then:
        maxLength == 512
    }

    def "DEFAULT exposes a stable singleton instance across repeated accesses"() {
        when:
        def a = AbstractLogConfig.DEFAULT
        def b = AbstractLogConfig.DEFAULT

        then:
        a.is(b)
    }

    def "a subclass overriding both abstracts replaces DEFAULT semantics for length and whitelist"() {
        given:
        def custom = new AbstractLogConfig() {
            @Override
            int getMaxMessageLength() { 1 }

            @Override
            boolean isWhitelisted(Class<?> type) { type == Thread }
        }

        expect:
        custom.isWhitelisted(Thread)
        !custom.isWhitelisted(String)
        !custom.isWhitelisted(Integer)
        !custom.isWhitelisted(null)
        custom.getMaxMessageLength() == 1
    }
}
