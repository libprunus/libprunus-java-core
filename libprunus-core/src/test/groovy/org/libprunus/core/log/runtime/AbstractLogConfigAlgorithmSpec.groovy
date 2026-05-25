package org.libprunus.core.log.runtime

import java.io.Serializable
import java.math.BigDecimal
import java.sql.Date as SqlDate
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.temporal.TemporalAccessor
import java.util.concurrent.atomic.AtomicInteger
import spock.lang.Specification

class AbstractLogConfigAlgorithmSpec extends Specification {

    private static final Class<?> ANONYMOUS_NUMBER_SUBTYPE = (new Number() {
        @Override int intValue() { 0 }
        @Override long longValue() { 0L }
        @Override float floatValue() { 0f }
        @Override double doubleValue() { 0d }
    }).getClass()

    def "DEFAULT isWhitelisted classifies every type sample to its expected acceptance state"() {
        given:
        def config = AbstractLogConfig.DEFAULT

        when:
        def result = config.isWhitelisted(type)

        then:
        result == expectedResult

        where: "the full type value pool spans null, exact matches, hierarchy edges, runtime-synthetic subclasses, JDK-internal classes, primitive traps, single/multi-dim arrays, and unrelated types"
        type                                 || expectedResult
        null                                 || false
        Boolean                              || true
        Character                            || true
        UUID                                 || true
        Class                                || true
        Enum                                 || true
        Number                               || true
        CharSequence                         || true
        TemporalAccessor                     || true
        Date                                 || true
        Month                                || true
        Integer                              || true
        Long                                 || true
        BigDecimal                           || true
        AtomicInteger                        || true
        String                               || true
        StringBuilder                        || true
        LocalDate                            || true
        LocalDateTime                        || true
        Instant                              || true
        Timestamp                            || true
        SqlDate                              || true
        ANONYMOUS_NUMBER_SUBTYPE             || true
        /* Primitive wrappers are matched via ==; their unboxed primitive counterparts
           (boolean.class, char.class, etc.) are not assignable from any branch and must be rejected. */
        Boolean.TYPE                         || false
        Character.TYPE                       || false
        Integer.TYPE                         || false
        Long.TYPE                            || false
        new String[0].class                  || false
        new Object[0].class                  || false
        new byte[0].class                    || false
        new int[0].class                     || false
        new int[0][0].class                  || false
        Collections.emptyList().getClass()   || false
        Object                               || false
        List                                 || false
        HashMap                              || false
        Thread                               || false
        AbstractLogConfig                    || false
        /* "== Class" strict-equality branch negative neighbors: a sub-interface and a super-interface
           of Class must both be rejected, proving the branch is strict equality and not isAssignableFrom. */
        Cloneable                            || false
        java.lang.reflect.Type               || false
        /* "== Boolean / == Character / == UUID" strict-equality branches: their shared super-interfaces
           must be rejected, proving the four == branches did not silently degrade into isAssignableFrom. */
        Comparable                           || false
        Serializable                         || false
    }
}
