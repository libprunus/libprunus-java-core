package org.libprunus.core.log.runtime

import java.time.Instant
import java.time.LocalDate
import java.util.AbstractCollection
import java.util.AbstractMap
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.libprunus.core.config.ConfigurationRepository
import org.libprunus.core.config.CoreRuntimeConfig
import org.slf4j.Logger
import spock.lang.Specification

class AotLogRuntimeSpec extends Specification {

    def setup() {
        AotLogRuntime.initialize(new CoreRuntimeConfig(new LogRuntimeConfig(true)))
    }

    def cleanup() {
        AotLogRuntime.initialize(new CoreRuntimeConfig(new LogRuntimeConfig(true)))
        CondyLoggerHolder.LIBPRUNUS_AOT_LOGGER = null
    }

    def "linkToDataPlane accepts empty reference and exposes downstream null-state failure"() {
        given: "an empty data-plane reference"
        def emptyRef = new AtomicReference<CoreRuntimeConfig>(null)

        when: "runtime is linked to the empty reference and state is queried"
        AotLogRuntime.linkToDataPlane(emptyRef)
        AotLogRuntime.isEnabled()

        then: "query fails because active config is null"
        thrown(NullPointerException)
    }

    def "isLevelEnabled short-circuits to false when runtime is globally disabled"() {
        given: "runtime disabled and null inputs"
        AotLogRuntime.updateConfig(new LogRuntimeConfig(false))

        expect: "global gate false short-circuits regardless of logger and level"
        !AotLogRuntime.isLevelEnabled(null, null)
    }

    def "isLevelEnabled throws when runtime enabled and level or logger is null"() {
        given: "runtime enabled"
        AotLogRuntime.updateConfig(new LogRuntimeConfig(true))

        when: "level and logger input pair is evaluated"
        AotLogRuntime.isLevelEnabled(logger, level)

        then: "null handling follows level dispatch semantics"
        thrown(NullPointerException)

        where:
        logger       | level
        null         | LogLevel.INFO
        Mock(Logger) | null
    }

    def "resolvePreconfiguredLogger returns null for non-fatal lookup failures"() {
        given: "lookup and owner combination"
        def lookup = rawLookup

        expect: "non-fatal failures are converted to null"
        AotLogRuntime.resolvePreconfiguredLogger(lookup, owner) == null

        where:
        rawLookup              | owner
        null                   | CondyLoggerHolder
        java.lang.invoke.MethodHandles.lookup() | null
        java.lang.invoke.MethodHandles.lookup() | CondyNoFieldHolder
        java.lang.invoke.MethodHandles.lookup() | CondyWrongFieldHolder
    }

    def "condyLoggerFactory ignores name and type and returns preconfigured logger when present"() {
        given: "owner with preconfigured logger"
        def expected = Mock(Logger)
        CondyLoggerHolder.LIBPRUNUS_AOT_LOGGER = expected
        def lookup = java.lang.invoke.MethodHandles.privateLookupIn(
                CondyLoggerHolder,
                java.lang.invoke.MethodHandles.lookup())

        expect: "name and type do not change result when owner has configured field"
        AotLogRuntime.condyLoggerFactory(lookup, name, type).is(expected)

        where:
        [name, type] << [
                [null, null],
                ["", Logger],
                ["TestLogger", Logger]
        ]
    }

    def "safeObjectToString three-arg overload handles null depth limit and whitelisted value boundaries"() {
        given: "a plain non-whitelisted object"
        def plain = new Object()

        expect: "null always renders null, depth check short-circuits, and whitelisted values render normally"
        AotLogRuntime.safeObjectToString(null, currentDepth, maxDepth) == "null"
        AotLogRuntime.safeObjectToString(plain, 5, 0) == "[DEPTH_LIMIT]"
        AotLogRuntime.safeObjectToString(plain, Integer.MAX_VALUE, 5) == "[DEPTH_LIMIT]"
        AotLogRuntime.safeObjectToString("String", 0, 5) == "String"

        where:
        currentDepth | maxDepth
        -1           | -1
        -1           | 0
        0            | -1
        0            | 5
        5            | 0
    }

    def "safeObjectToString four-arg overload handles negative maxElements and depth short-circuit"() {
        given: "a fixed list value"
        def values = [1, 2, 3]

        expect: "null rendering, depth guard, negative element cap normalization and truncation all stay stable"
        AotLogRuntime.safeObjectToString(null, currentDepth, maxElements, maxDepth) == "null"
        AotLogRuntime.safeObjectToString(values, 10, 100, 0) == "[DEPTH_LIMIT]"
        AotLogRuntime.safeObjectToString(values, 0, -1, 10) == "[... (truncated, size=3)]"
        AotLogRuntime.safeObjectToString(values, 0, 2, 10) == "[1, 2, ... (truncated, size=3)]"

        where:
        currentDepth | maxElements | maxDepth
        0            | 0           | 0
        0            | 2           | 10
        10           | 100         | 0
    }

    def "safeArrayToString four-arg object overload handles null non-array depth and array dispatch"() {
        given: "a non-array plain object"
        def plain = new Object()

        expect: "generic overload keeps null handling, depth guard, non-array degradation and object-array dispatch"
        AotLogRuntime.safeArrayToString(null, 0, 10, 5) == "null"
        AotLogRuntime.safeArrayToString(([1] as int[]), 5, 10, 0) == "[DEPTH_LIMIT]"
        AotLogRuntime.safeArrayToString(plain, 0, 10, 5) == "java.lang.Object@${Integer.toHexString(System.identityHashCode(plain))}"
        AotLogRuntime.safeArrayToString((["a"] as String[]), 0, 10, 5) == "[a]"
    }

    def "typed safeArrayToString overloads keep null marker depth guard and zero-cap truncation"() {
        expect: "all typed overloads share identical control-flow semantics"
        renderNull.call() == "null"
        renderDepthLimit.call() == "[DEPTH_LIMIT]"
        renderZeroCap.call() == "[... (truncated, size=2)]"

        where:
        renderNull                                                                 || renderDepthLimit                                                                      || renderZeroCap
        ({ -> AotLogRuntime.safeArrayToString((boolean[]) null, 0, 0, 0) })       || ({ -> AotLogRuntime.safeArrayToString(([true, false] as boolean[]), 10, 10, 0) })   || ({ -> AotLogRuntime.safeArrayToString(([true, false] as boolean[]), 0, 0, 5) })
        ({ -> AotLogRuntime.safeArrayToString((byte[]) null, 0, 0, 0) })          || ({ -> AotLogRuntime.safeArrayToString(([1, 2] as byte[]), 10, 10, 0) })              || ({ -> AotLogRuntime.safeArrayToString(([1, 2] as byte[]), 0, 0, 5) })
        ({ -> AotLogRuntime.safeArrayToString((char[]) null, 0, 0, 0) })          || ({ -> AotLogRuntime.safeArrayToString((['a', 'b'] as char[]), 10, 10, 0) })          || ({ -> AotLogRuntime.safeArrayToString((['a', 'b'] as char[]), 0, 0, 5) })
        ({ -> AotLogRuntime.safeArrayToString((short[]) null, 0, 0, 0) })         || ({ -> AotLogRuntime.safeArrayToString(([1, 2] as short[]), 10, 10, 0) })             || ({ -> AotLogRuntime.safeArrayToString(([1, 2] as short[]), 0, 0, 5) })
        ({ -> AotLogRuntime.safeArrayToString((int[]) null, 0, 0, 0) })           || ({ -> AotLogRuntime.safeArrayToString(([1, 2] as int[]), 10, 10, 0) })               || ({ -> AotLogRuntime.safeArrayToString(([1, 2] as int[]), 0, 0, 5) })
        ({ -> AotLogRuntime.safeArrayToString((long[]) null, 0, 0, 0) })          || ({ -> AotLogRuntime.safeArrayToString(([1L, 2L] as long[]), 10, 10, 0) })            || ({ -> AotLogRuntime.safeArrayToString(([1L, 2L] as long[]), 0, 0, 5) })
        ({ -> AotLogRuntime.safeArrayToString((float[]) null, 0, 0, 0) })         || ({ -> AotLogRuntime.safeArrayToString(([1.0f, 2.0f] as float[]), 10, 10, 0) })       || ({ -> AotLogRuntime.safeArrayToString(([1.0f, 2.0f] as float[]), 0, 0, 5) })
        ({ -> AotLogRuntime.safeArrayToString((double[]) null, 0, 0, 0) })        || ({ -> AotLogRuntime.safeArrayToString(([1.0d, 2.0d] as double[]), 10, 10, 0) })      || ({ -> AotLogRuntime.safeArrayToString(([1.0d, 2.0d] as double[]), 0, 0, 5) })
        ({ -> AotLogRuntime.safeArrayToString((Object[]) null, 0, 0, 0) })        || ({ -> AotLogRuntime.safeArrayToString((["a", "b"] as Object[]), 10, 10, 0) })      || ({ -> AotLogRuntime.safeArrayToString((["a", "b"] as Object[]), 0, 0, 5) })
    }

    def "appendObjectTo handles null and depth guard"() {
        given: "a builder"
        def builder = new StringBuilder()

        when: "null is rendered"
        AotLogRuntime.appendObjectTo(builder, null, 0, 10, 5)

        then: "null literal is appended"
        builder.toString() == "null"

        when: "depth limit is hit for non-null value"
        builder.setLength(0)
        AotLogRuntime.appendObjectTo(builder, new Object(), 0, 10, 0)

        then: "depth marker is appended"
        builder.toString() == "[DEPTH_LIMIT]"
    }

    def "appendObjectTo dispatches array loggable collection map scalar and fallback branches"() {
        given: "a builder"
        def builder = new StringBuilder()

        when: "value is rendered"
        AotLogRuntime.appendObjectTo(builder, valueProvider.call(), 0, 10, 5)

        then: "output matches dispatch branch behavior"
        assertResult.call(builder.toString())

        where:
        valueProvider                                  | assertResult
        ({ -> ([1] as int[]) })                       | ({ rendered -> rendered == "[1]" })
        ({ -> new LoggableValue("payload") })        | ({ rendered -> rendered == "loggable(payload@1)" })
        ({ -> ["a"] })                               | ({ rendered -> rendered == "[a]" })
        ({ -> Collections.singletonMap("k", "v") }) | ({ rendered -> rendered == "{k=v}" })
        ({ -> "str" })                               | ({ rendered -> rendered == "str" })
        ({ -> new Object() })                         | ({ rendered -> rendered ==~ /java\.lang\.Object@[0-9a-f]+/ })
    }

    def "appendArrayTo object dispatcher covers short-circuit branches and typed routing"() {
        given: "a builder"
        def builder = new StringBuilder()

        when: "object-array dispatcher is invoked"
        AotLogRuntime.appendArrayTo(builder, inputValue, currentDepth, 10, maxDepth)

        then: "rendering matches fallback or typed-array routing"
        verifyResult.call(builder.toString())

        where:
        inputValue              | currentDepth | maxDepth | verifyResult
        null                    | 0            | 5        | ({ rendered -> rendered == "null" })
        "x"                    | 0            | 5        | ({ rendered -> rendered == "x" })
        "Not an array"         | 0            | 5        | ({ rendered -> rendered == "Not an array" })
        ([true, false] as boolean[]) | 0       | 5        | ({ rendered -> rendered == "[true, false]" })
        ([1, 2] as byte[])      | 0            | 5        | ({ rendered -> rendered == "[1, 2]" })
        (['a', 'b'] as char[])  | 0            | 5        | ({ rendered -> rendered == "[a, b]" })
        ([3, 4] as short[])     | 0            | 5        | ({ rendered -> rendered == "[3, 4]" })
        ([1] as int[])          | 0            | 5        | ({ rendered -> rendered == "[1]" })
        ([7L, 8L] as long[])    | 0            | 5        | ({ rendered -> rendered == "[7, 8]" })
        ([1.5f, 2.5f] as float[]) | 0          | 5        | ({ rendered -> rendered == "[1.5, 2.5]" })
        ([3.5d, 4.5d] as double[]) | 0         | 5        | ({ rendered -> rendered == "[3.5, 4.5]" })
        ([1] as int[])          | 5            | 0        | ({ rendered -> rendered == "[DEPTH_LIMIT]" })
        ([new Object()] as Object[]) | 0       | 5        | ({ rendered -> rendered ==~ /\[java\.lang\.Object@[0-9a-f]+\]/ })
    }

    def "appendCollectionTo and appendMapTo honor empty and element-limit branches"() {
        given: "builders"
        def collectionBuilder = new StringBuilder()
        def mapBuilder = new StringBuilder()

        expect: "collection rendering obeys maxElements limits"
        AotLogRuntime.appendCollectionTo(collectionBuilder, value, 0, maxElements, 10)
        collectionBuilder.toString() == expectedCollection

        and: "map rendering obeys maxElements limits"
        AotLogRuntime.appendMapTo(mapBuilder, mapValue, 0, mapMaxElements, 10)
        mapBuilder.toString() == expectedMap

        where:
        value       | maxElements || expectedCollection                    | mapValue | mapMaxElements || expectedMap
        []          | 2           || "[]"                                  | [:]               | 2      || "{}"
        []          | -1          || "[]"                                  | [a: 1, b: 2, c: 3] | 0      || "{... (truncated, size=3)}"
        [1]         | 0           || "[... (truncated, size=1)]"           | [k: 1]            | 0      || "{... (truncated, size=1)}"
        [1]         | 2           || "[1]"                                 | [k: 1]            | 2      || "{k=1}"
        [1, 2, 3]   | -1          || "[... (truncated, size=3)]"           | [a: 1, b: 2, c: 3] | 2      || "{a=1, b=2, ... (truncated, size=3)}"
        [1, 2, 3]   | 0           || "[... (truncated, size=3)]"           | [a: 1, b: 2, c: 3] | 100    || "{a=1, b=2, c=3}"
        [1, 2, 3]   | 2           || "[1, 2, ... (truncated, size=3)]"     | [k: 1]   | 100            || "{k=1}"
        [1, 2, 3]   | 100         || "[1, 2, 3]"                           | [k: 1]   | 100            || "{k=1}"
    }

    def "appendSafeScalarTo and appendIdentityTo render scalar variants and fallback identity"() {
        given: "a scalar builder and identity builder"
        def scalarBuilder = new StringBuilder()
        def identityBuilder = new StringBuilder()
        def identityValue = new Object()

        when: "safe scalar branch is invoked"
        AotLogRuntime.appendSafeScalarTo(scalarBuilder, value)

        then: "expected scalar rendering is appended"
        scalarBuilder.toString() == expected

        when: "identity branch is invoked"
        AotLogRuntime.appendIdentityTo(identityBuilder, identityValue)

        then: "identity rendering uses class plus identity hash"
        identityBuilder.toString() ==~ /java\.lang\.Object@[0-9a-f]+/

        where:
        value             || expected
        "String"          || "String"
        Boolean.TRUE      || "true"
        ('c' as char)     || "c"
        Thread.State.NEW  || "NEW"
        String            || "class java.lang.String"
        123               || "123"
    }

    def "appendPrimitiveTo for int array honors empty zero partial and full limits"() {
        given: "a builder"
        def builder = new StringBuilder()

        when: "primitive int array appender is invoked"
        AotLogRuntime.appendPrimitiveTo(builder, value, maxElements)

        then: "array rendering follows truncation contract"
        builder.toString() == expected

        where:
        value          | maxElements || expected
        ([] as int[])  | -1          || "[]"
        ([] as int[])  | 10          || "[]"
        ([1, 2, 3] as int[]) | -1     || "[... (truncated, size=3)]"
        ([1, 2, 3] as int[]) | 0      || "[... (truncated, size=3)]"
        ([1, 2, 3] as int[]) | 1      || "[1, ... (truncated, size=3)]"
        ([1, 2, 3] as int[]) | 2      || "[1, 2, ... (truncated, size=3)]"
        ([1, 2, 3] as int[]) | 10     || "[1, 2, 3]"
    }

    def "appendTruncation appends suffix only when size exceeds limit"() {
        given: "a builder"
        def builder = new StringBuilder(prefix)

        when: "truncation helper is invoked"
        AotLogRuntime.appendTruncation(builder, size, limit)

        then: "suffix behavior follows size and limit relationship"
        builder.toString() == expected

        where:
        prefix | size | limit || expected
        ""     | 3    | 5     || ""
        ""     | 3    | 0     || "... (truncated, size=3)"
        "x"    | 3    | 2     || "x, ... (truncated, size=3)"
    }

    def "safeObjectToString delegates to AotLoggable render method with incremented depth"() {
        given: "a mock AotLoggable whose render method appends a fixed token"
        def mockLoggable = Mock(AotLoggable)

        when: "the three-arg overload is called with depth 0 and max 10"
        def result = AotLogRuntime.safeObjectToString(mockLoggable, 0, 10)

        then: "the render contract is invoked exactly once with a StringBuilder and depth currentDepth + 1"
        1 * mockLoggable._libprunus_render({ it instanceof StringBuilder }, 1)
        result != null
    }

    def "safeObjectToString returns depth limit marker when current depth reaches maximum"() {
        given: "any plain object"
        def normalObject = new HashMap<>()

        when: "four-arg overload is called with currentDepth equal to maxDepth"
        def result = AotLogRuntime.safeObjectToString(normalObject, 5, 100, 5)

        then: "the depth limit marker is returned without inspecting the object"
        result == "[DEPTH_LIMIT]"
    }

    def "safeArrayToString truncates object array output when size exceeds maxElements"() {
        given: "a five-element object array"
        def largeArray = ["a", "b", "c", "d", "e"] as Object[]

        when: "the four-arg overload is called with maxElements of 2"
        def result = AotLogRuntime.safeArrayToString(largeArray, 0, 2, 10)

        then: "the first two elements are rendered"
        result.startsWith("[a, b,")

        and: "the truncation suffix reflects the full array size"
        result.endsWith("... (truncated, size=5)]")
    }

    def "safeArrayToString truncates primitive array output when size exceeds maxElements"() {
        when: "the overload is called with maxElements of 1"
        def result = AotLogRuntime.safeArrayToString(arrayValue, 0, 1, 10)

        then: "the first element is rendered and the truncation suffix reflects the full size"
        result.startsWith("[${firstElement},")
        result.endsWith("... (truncated, size=2)]")

        where:
        arrayValue                   | firstElement
        ([true, false] as boolean[]) | "true"
        ([1, 2] as byte[])           | "1"
        (['a', 'b'] as char[])       | "a"
        ([3, 4] as short[])          | "3"
        ([5, 6] as int[])            | "5"
        ([7L, 8L] as long[])         | "7"
        ([1.5f, 2.5f] as float[])    | "1.5"
        ([3.5d, 4.5d] as double[])   | "3.5"
    }

    def "initialize configures the runtime to the enabled state specified by the given config"() {
        when: "runtime is initialized with the given config"
        AotLogRuntime.initialize(new CoreRuntimeConfig(new LogRuntimeConfig(enabled)))

        then: "runtime gateway reflects the configured enabled state"
        AotLogRuntime.isEnabled() == enabled

        where:
        enabled << [true, false]
    }

    def "linkToDataPlane binds the runtime gate to the shared config reference and reflects live mutations"() {
        given: "a shared config reference starting disabled"
        def configRef = new AtomicReference<CoreRuntimeConfig>(new CoreRuntimeConfig(new LogRuntimeConfig(false)))

        when: "runtime is linked to the shared config reference"
        AotLogRuntime.linkToDataPlane(configRef)

        then: "initial disabled state from the shared reference is reflected"
        !AotLogRuntime.isEnabled()

        when: "the shared reference is updated to enabled"
        configRef.set(new CoreRuntimeConfig(new LogRuntimeConfig(true)))

        then: "runtime gate immediately reflects the live mutation"
        AotLogRuntime.isEnabled()
    }

    def "updateConfig changes runtime state for valid config and ignores null input"() {
        given: "runtime initialized to the given initial state"
        AotLogRuntime.initialize(new CoreRuntimeConfig(new LogRuntimeConfig(initialEnabled)))

        when: "updateConfig is called with the given config"
        AotLogRuntime.updateConfig(logConfig)

        then: "runtime reports the expected enabled state"
        AotLogRuntime.isEnabled() == expected

        where:
        initialEnabled | logConfig                   | expected
        true           | new LogRuntimeConfig(false) | false
        false          | null                        | false
    }

    def "ACTIVE_CONFIG_REF holds a live AtomicReference to the current CoreRuntimeConfig"() {
        expect: "the runtime config field holds a live AtomicReference containing the active snapshot"
        AotLogRuntime.@ACTIVE_CONFIG_REF instanceof AtomicReference
        (AotLogRuntime.@ACTIVE_CONFIG_REF as AtomicReference).get() instanceof CoreRuntimeConfig
    }

    def "initialize updates the shared config reference without detaching the data plane link"() {
        given: "a shared reference already linked to runtime"
        def shared = new AtomicReference(new CoreRuntimeConfig(new LogRuntimeConfig(false)))
        AotLogRuntime.linkToDataPlane(shared)

        when: "initialize is called after link and the shared reference is updated again"
        AotLogRuntime.initialize(new CoreRuntimeConfig(new LogRuntimeConfig(true)))
        shared.set(new CoreRuntimeConfig(new LogRuntimeConfig(false)))

        then: "runtime remains attached to the same shared reference"
        !AotLogRuntime.isEnabled()
        shared.get().log().enabled() == false
    }

    def "condyLoggerFactory falls back to SLF4J for any non-fatal lookup failure"() {
        given: "CondyInaccessibleHolder has a preconfigured logger for the access-failure scenario"
        CondyInaccessibleHolder.setLogger(Mock(Logger))

        and: "a lookup configured for the holder under test"
        def lookup = lookupProvider()

        when: "the condy bootstrap is invoked"
        def result = AotLogRuntime.condyLoggerFactory(lookup, "LIBPRUNUS_AOT_LOGGER", Logger)

        then: "a non-null SLF4J logger is resolved using the lookup class as the logger name"
        result != null
        result.name == expectedName

        cleanup:
        CondyInaccessibleHolder.setLogger(null)

        where:
        lookupProvider                                                                                                                         | expectedName
        ({ -> java.lang.invoke.MethodHandles.privateLookupIn(CondyNoFieldHolder, java.lang.invoke.MethodHandles.lookup()) })                    | CondyNoFieldHolder.name
        ({ -> java.lang.invoke.MethodHandles.publicLookup().in(CondyInaccessibleHolder) })                                                      | CondyInaccessibleHolder.name
    }

    def "isLevelEnabled delegates to the matching logger-level probe and propagates its result"() {
        given: "runtime is globally enabled and every level probe is pre-stubbed for the iteration"
        AotLogRuntime.updateConfig(new LogRuntimeConfig(true))
        def mockLogger = Mock(Logger) {
            isTraceEnabled() >> (level == LogLevel.TRACE && loggerEnabled)
            isDebugEnabled() >> (level == LogLevel.DEBUG && loggerEnabled)
            isInfoEnabled()  >> (level == LogLevel.INFO  && loggerEnabled)
            isWarnEnabled()  >> (level == LogLevel.WARN  && loggerEnabled)
            isErrorEnabled() >> (level == LogLevel.ERROR && loggerEnabled)
        }

        when: "level enablement is checked"
        def result = AotLogRuntime.isLevelEnabled(mockLogger, level)

        then: "the result propagates the logger-level probe's return value"
        result == expected

        where:
        level          | loggerEnabled | expected
        LogLevel.TRACE | true          | true
        LogLevel.DEBUG | false         | false
        LogLevel.INFO  | true          | true
        LogLevel.WARN  | false         | false
        LogLevel.ERROR | true          | true
    }

    def "safeObjectToString renders collection elements and appends truncation marker when exceeding limit"() {
        given: "a three-element list"
        def listCollection = new ArrayList<>(List.of("A", "B", "C"))

        when: "rendering with maxElements of 2"
        def result = AotLogRuntime.safeObjectToString(listCollection, 0, 2, 10)

        then: "first two elements are rendered and truncation marker reflects full size"
        result == "[A, B, ... (truncated, size=3)]"
    }

    def "safeObjectToString renders map key-value pairs and appends truncation marker when exceeding limit"() {
        given: "a two-entry ordered map"
        def mapData = new LinkedHashMap<String, String>()
        mapData["K1"] = "V1"
        mapData["K2"] = "V2"

        when: "rendering with maxElements of 1"
        def result = AotLogRuntime.safeObjectToString(mapData, 0, 1, 10)

        then: "first entry is rendered and truncation marker reflects full size"
        result == "{K1=V1, ... (truncated, size=2)}"
    }

    def "safeObjectToString three-arg overload applies DEFAULT_MAX_RENDER_ELEMENTS constant to collection rendering"() {
        given: "a list containing 105 elements, exceeding the default cap of 100"
        def largeList = new ArrayList<Integer>(105)
        (1..105).each { largeList.add(it) }

        when: "the three-arg overload is called without an explicit maxElements argument"
        def result = AotLogRuntime.safeObjectToString(largeList, 0, 10)

        then: "truncation is triggered at the default limit and the marker reflects the full collection size"
        result.endsWith("... (truncated, size=105)]")
    }

    def "safeArrayToString falls back to object rendering when the input value is not an array"() {
        given: "a plain string value passed to the generic array serializer"
        def nonArrayObject = "NotAnArray"

        when: "the four-arg generic overload is called with the non-array input"
        def result = AotLogRuntime.safeArrayToString(nonArrayObject, 0, 10, 10)

        then: "the non-array guard redirects to scalar rendering and the original string is returned"
        result == "NotAnArray"
    }

    def "typed safeArrayToString four-arg overloads render all primitive element types with explicit cast dispatch"() {
        expect: "inline-cast arguments trigger direct typed-overload dispatch for each primitive type"
        AotLogRuntime.safeArrayToString(([true, false] as boolean[]), 0, 10, 5) == "[true, false]"
        AotLogRuntime.safeArrayToString(([1, 2] as byte[]),           0, 10, 5) == "[1, 2]"
        AotLogRuntime.safeArrayToString((['a', 'b'] as char[]),       0, 10, 5) == "[a, b]"
        AotLogRuntime.safeArrayToString(([3, 4] as short[]),          0, 10, 5) == "[3, 4]"
        AotLogRuntime.safeArrayToString(([5, 6] as int[]),            0, 10, 5) == "[5, 6]"
        AotLogRuntime.safeArrayToString(([7L, 8L] as long[]),         0, 10, 5) == "[7, 8]"
        AotLogRuntime.safeArrayToString(([1.5f, 2.5f] as float[]),    0, 10, 5) == "[1.5, 2.5]"
        AotLogRuntime.safeArrayToString(([3.5d, 4.5d] as double[]),   0, 10, 5) == "[3.5, 4.5]"
    }

    def "appendArrayTo direct cartesian matrix validates typed dispatch and depth short-circuit"() {
        given: "a builder"
        def builder = new StringBuilder()

        when: "appendArrayTo is directly invoked with matrix-generated inputs"
        AotLogRuntime.appendArrayTo(builder, arrayValue, currentDepth, maxElements, maxDepth)

        then: "rendering matches either typed dispatch or depth short-circuit"
        verifyResult.call(builder.toString())

        where:
        [arrayValue, currentDepth, maxElements, maxDepth, verifyResult] << [[
            [([true, false] as boolean[]), ({ rendered -> rendered == "[true, false]" })],
            [([1, 2] as byte[]), ({ rendered -> rendered == "[1, 2]" })],
            [(['a', 'b'] as char[]), ({ rendered -> rendered == "[a, b]" })],
            [([3, 4] as short[]), ({ rendered -> rendered == "[3, 4]" })],
            [([5, 6] as int[]), ({ rendered -> rendered == "[5, 6]" })],
            [([7L, 8L] as long[]), ({ rendered -> rendered == "[7, 8]" })],
            [([1.5f, 2.5f] as float[]), ({ rendered -> rendered == "[1.5, 2.5]" })],
            [([3.5d, 4.5d] as double[]), ({ rendered -> rendered == "[3.5, 4.5]" })],
            [([new Object()] as Object[]), ({ rendered -> rendered ==~ /\[java\.lang\.Object@[0-9a-f]+\]/ })]
        ], [
            [0, 10, 5, false],
            [5, 10, 0, true]
        ]]
                .combinations()
                .collect { row ->
                    def typedCase = row[0]
                    def runtimeCase = row[1]
                    def depthLimited = runtimeCase[3]
                    [
                            typedCase[0],
                            runtimeCase[0],
                            runtimeCase[1],
                            runtimeCase[2],
                            depthLimited ? ({ rendered -> rendered == "[DEPTH_LIMIT]" }) : typedCase[1]
                    ]
                }
    }

    def "safeArrayToString routes every primitive array overload"() {
        when: "a primitive array is rendered"
        def rendered = AotLogRuntime.safeArrayToString(arrayValue, 10, 5)

        then: "the matching primitive formatter is used"
        rendered == expected

        where:
        arrayValue                    || expected
        ([true, false] as boolean[])  || "[true, false]"
        ([1, 2] as byte[])            || "[1, 2]"
        (['a', 'b'] as char[])        || "[a, b]"
        ([3, 4] as short[])           || "[3, 4]"
        ([5, 6] as int[])             || "[5, 6]"
        ([7L, 8L] as long[])          || "[7, 8]"
        ([1.5f, 2.5f] as float[])     || "[1.5, 2.5]"
        ([3.5d, 4.5d] as double[])    || "[3.5, 4.5]"
    }

    static class CondyLinkageErrorHolder {
        static Logger LIBPRUNUS_AOT_LOGGER = initLogger()

        private static Logger initLogger() {
            throw new LinkageError("condy-linkage")
        }
    }

    def "initialize with null config rejects with precise validation message"() {
        when: "initialize is called with null configuration"
        AotLogRuntime.initialize(null)

        then: "NullPointerException is thrown with the exact validation message"
        def exception = thrown(NullPointerException)
        exception.message == "initialConfig must not be null"
    }

    def "linkToDataPlane with null reference rejects with precise validation message"() {
        when: "linkToDataPlane is called with null atomic reference"
        AotLogRuntime.linkToDataPlane(null)

        then: "NullPointerException is thrown with the exact validation message"
        def exception = thrown(NullPointerException)
        exception.message == "configRef must not be null"
    }

    def "condyLoggerFactory propagates fatal error with its exact message during logger resolution"() {
        given: "a lookup holder whose static logger initialization fails fatally"
        def lookup = java.lang.invoke.MethodHandles.privateLookupIn(holder, java.lang.invoke.MethodHandles.lookup())

        when: "the factory attempts to resolve the preconfigured logger"
        AotLogRuntime.condyLoggerFactory(lookup, "testName", Object.class)

        then: "the fatal error is propagated with its exact message"
        def error = thrown(Error)
        error.message == expectedMessage

        where:
        holder                      | expectedMessage
        CondyOutOfMemoryErrorHolder | "heap-space-exhausted"
        CondyErrorHolder            | "condy-fatal"
    }

    def "condyLoggerFactory propagates LinkageError during preconfigured logger resolution"() {
        given: "a lookup holder whose static logger initialization fails with LinkageError"
        def lookup = java.lang.invoke.MethodHandles.privateLookupIn(
                CondyLinkageErrorHolder,
                java.lang.invoke.MethodHandles.lookup())

        when: "the factory attempts to resolve the preconfigured logger"
        AotLogRuntime.condyLoggerFactory(lookup, "testName", Object.class)

        then: "the fatal error from the initialization chain is propagated"
        def error = thrown(Error)
        def cause = error.cause?.cause ?: error.cause
        cause?.cause?.message == "condy-linkage" || cause?.message?.contains("condy-linkage") || error.message.contains("condy-linkage")
    }

    def "condyLoggerFactory with null lookup implicitly throws NullPointerException on class resolution"() {
        when: "factory is invoked with a null Lookup reference"
        AotLogRuntime.condyLoggerFactory((java.lang.invoke.MethodHandles.Lookup) null, "testName", Object.class)

        then: "NullPointerException is thrown implicitly when invoking lookup.lookupClass()"
        thrown(NullPointerException)
    }

    def "isLevelEnabled with null level throws when runtime is globally enabled"() {
        given: "runtime is globally enabled"
        AotLogRuntime.updateConfig(new LogRuntimeConfig(true))
        def mockLogger = Mock(Logger)

        when: "level enablement is checked with a null log level"
        AotLogRuntime.isLevelEnabled(mockLogger, null)

        then: "NullPointerException is thrown implicitly when invoking level.isEnabled()"
        thrown(NullPointerException)

        and: "no interaction occurs with the logger instance during the failure"
        0 * mockLogger._
    }

    static class CondyOutOfMemoryErrorHolder {
        static Logger LIBPRUNUS_AOT_LOGGER = initLogger()

        private static Logger initLogger() {
            throw new OutOfMemoryError("heap-space-exhausted")
        }
    }

    def "safeObjectToString propagates RuntimeException from AotLoggable render callback"() {
        given: "an AotLoggable implementation whose render method throws RuntimeException"
        def failingLoggable = new FailingAotLoggable("render-failed")

        when: "attempting to render the failing loggable object"
        AotLogRuntime.safeObjectToString(failingLoggable, 0, 5)

        then: "the rendering exception is propagated to the caller"
        def exception = thrown(RuntimeException)
        exception.message == "render-failed"
    }

    def "safeObjectToString propagates exception from collection iteration failure"() {
        given: "a collection whose iterator throws after showing initial elements"
        def failingCollection = new FailingIteratorCollection()

        when: "attempting to render a collection that fails mid-iteration"
        AotLogRuntime.safeObjectToString(failingCollection, 0, 10, 5)

        then: "the iteration exception is propagated to the caller"
        def exception = thrown(RuntimeException)
        exception.message == "iteration-failed"
    }

    def "safeObjectToString returns depth marker when depth limit is exhausted"() {
        when: "rendering an object with the given depth arguments"
        def result = AotLogRuntime.safeObjectToString(new Object(), currentDepth, maxDepth)

        then: "the depth limit marker is returned immediately"
        result == "[DEPTH_LIMIT]"

        where:
        currentDepth | maxDepth
        0            | -1
        0            | 0
        5            | 5
        5            | 4
    }

    def "safeArrayToString handles negative maxDepth with boundary conditions"() {
        when: "rendering arrays with negative or zero maxDepth"
        def negativeResult = AotLogRuntime.safeArrayToString([1, 2] as int[], 0, -1)
        def zeroResult = AotLogRuntime.safeArrayToString([3, 4] as int[], 0, 0)

        then: "depth guard prevents traversal regardless of initial depth"
        negativeResult == "[DEPTH_LIMIT]"
        zeroResult == "[DEPTH_LIMIT]"
    }

    static class FailingAotLoggable implements AotLoggable {
        final String failureMessage

        FailingAotLoggable(String failureMessage) {
            this.failureMessage = failureMessage
        }

        @Override
        void _libprunus_render(StringBuilder builder, int currentDepth) {
            throw new RuntimeException(failureMessage)
        }
    }

    static class FailingIteratorCollection extends AbstractCollection<Object> {
        @Override
        Iterator<Object> iterator() {
            new Iterator<Object>() {
                private int count

                @Override
                boolean hasNext() {
                    if (count > 0) {
                        throw new RuntimeException("iteration-failed")
                    }
                    count < 1
                }

                @Override
                Object next() {
                    count++
                    "element"
                }
            }
        }

        @Override
        int size() {
            1
        }
    }

    def "repository projection updates runtime state"() {
        given: "a repository initialized with disabled runtime config"
        def repository = new ConfigurationRepository(new CoreRuntimeConfig(new LogRuntimeConfig(false)))

        when: "repository refresh projects an enabled runtime config"
        repository.refresh(new CoreRuntimeConfig(new LogRuntimeConfig(true)))

        then: "the runtime gateway becomes enabled"
        AotLogRuntime.isEnabled()
    }

    def "LogLevel dispatch routes all levels with and without throwable"() {
        given: "a logger and message payload"
        def logger = Mock(Logger)
        def throwable = withThrowable ? new IllegalStateException("boom") : null

        when: "the level dispatcher is invoked"
        level.dispatch(logger, "message", throwable)

        then: "exactly one logger overload matching the level and throwable presence is called"
        if (withThrowable) {
            1 * logger."${level.name().toLowerCase()}"("message", throwable)
        } else {
            1 * logger."${level.name().toLowerCase()}"("message")
        }

        where:
        [level, withThrowable] << [
                [LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR],
                [false, true]
        ].combinations()
    }

    def "collection and map appenders stop immediately at depth limit"() {
        given: "builders for collection and map rendering"
        def collectionBuilder = new StringBuilder()
        def mapBuilder = new StringBuilder()

        when: "nested container appenders are invoked at the depth limit"
        AotLogRuntime.appendCollectionTo(collectionBuilder, [1], 3, 4, 3)
        AotLogRuntime.appendMapTo(mapBuilder, [a: 1], 3, 4, 3)

        then: "they emit the depth marker immediately"
        collectionBuilder.toString() == "[DEPTH_LIMIT]"
        mapBuilder.toString() == "[DEPTH_LIMIT]"
    }

    def "safe array rendering honors zero element limit and exhausted depth"() {
        expect: "array helpers avoid rendering elements when limits are already exhausted"
        AotLogRuntime.safeArrayToString(([true] as boolean[]), 0, 0, 3) == "[... (truncated, size=1)]"
        AotLogRuntime.safeArrayToString((["value"] as Object[]), 2, 4, 2) == "[DEPTH_LIMIT]"
    }

    def "private constructor throws unsupported operation when invoked through Groovy"() {
        when: "the private constructor is invoked through Groovy"
        new AotLogRuntime()

        then: "the constructor rejects instantiation"
        thrown(UnsupportedOperationException)
    }

    def "safeObjectToString dispatches to loggable contract and applies depth guard"() {
        given: "a custom loggable value"
        def value = new LoggableValue("payload")

        expect: "depth-aware dispatch uses contract call under limit and depth marker at limit"
        AotLogRuntime.safeObjectToString(value, 0, 2) == "loggable(payload@1)"
        AotLogRuntime.safeObjectToString(value, 2, 2) == "[DEPTH_LIMIT]"
    }

    def "safeObjectToString keeps whitelisted scalar values readable"() {
        given: "stable temporal and identifier values"
        def uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        def date = new Date(0)
        def localDate = LocalDate.of(2026, 3, 30)
        def instant = Instant.ofEpochSecond(0)

        expect: "whitelisted scalar values use direct readable rendering"
        AotLogRuntime.safeObjectToString("plain", 0, 2) == "plain"
        AotLogRuntime.safeObjectToString(new StringBuilder("buf"), 0, 2) == "buf"
        AotLogRuntime.safeObjectToString(7, 0, 2) == "7"
        AotLogRuntime.safeObjectToString(true, 0, 2) == "true"
        AotLogRuntime.safeObjectToString('x', 0, 2) == "x"
        AotLogRuntime.safeObjectToString(uuid, 0, 2) == uuid.toString()
        AotLogRuntime.safeObjectToString(date, 0, 2) == date.toString()
        AotLogRuntime.safeObjectToString(localDate, 0, 2) == "2026-03-30"
        AotLogRuntime.safeObjectToString(instant, 0, 2) == "1970-01-01T00:00:00Z"
        AotLogRuntime.safeObjectToString(SampleState.READY, 0, 2) == "READY"
        AotLogRuntime.safeObjectToString(String, 0, 2) == "class java.lang.String"
    }

    def "safeObjectToString does not invoke overridden toString on non-whitelisted objects"() {
        given: "an object whose toString should never run on the fallback path"
        NonWhitelistedValue.toStringCalls = 0
        def value = new NonWhitelistedValue()

        when: "the value is rendered"
        def rendered = AotLogRuntime.safeObjectToString(value, 0, 2)

        then: "identity rendering is used without calling the custom toString"
        rendered == "${NonWhitelistedValue.name}@${Integer.toHexString(System.identityHashCode(value))}"
        NonWhitelistedValue.toStringCalls == 0
    }

    def "safeObjectToString renders enum constant-specific class bodies as enum names"() {
        expect: "enum subclasses generated for constant class bodies are still treated as safe enum values"
        AotLogRuntime.safeObjectToString(PolymorphicState.HOT, 0, 2) == "HOT"
    }

    def "safeObjectToString propagates iteration failures from collection and map rendering"() {
        given: "values whose iterators fail after yielding the first element"
        def collection = new FailingCollection(["first", "second"])
        def map = new FailingMap([first: 1, second: 2])

        when: "rendering iterates the failing collection"
        AotLogRuntime.safeObjectToString(collection, 0, 5, 4)

        then: "collection iteration failure is propagated"
        thrown(ConcurrentModificationException)

        when: "rendering iterates the failing map"
        AotLogRuntime.safeObjectToString(map, 0, 5, 4)

        then: "map rendering iteration failure is propagated"
        thrown(ConcurrentModificationException)
    }

    def "appendObjectTo renders nested arrays into the provided builder"() {
        given: "an existing builder and a nested collection of byte arrays"
        def builder = new StringBuilder("payload=")
        def value = [[1, 2] as byte[], [3, 4] as byte[]]

        when: "the append contract is used directly"
        AotLogRuntime.appendObjectTo(builder, value, 0, 5, 4)

        then: "all nested content is written into the same builder"
        builder.toString() == "payload=[[1, 2], [3, 4]]"
    }

    def "safeObjectToString applies one global element quota across nested containers"() {
        given: "a nested collection structure that would exceed per-level rendering limits"
        def nested = [
                [1, 2, 3],
                [4, 5, 6],
                [7, 8, 9]
        ]

        when: "rendering uses a small maxElements budget"
        def rendered = AotLogRuntime.safeObjectToString(nested, 0, 4, 8)

        then: "nested containers are rendered fully under current per-container element limits"
        rendered == "[[1, 2, 3], [4, 5, 6], [7, 8, 9]]"
    }

    def "safeObjectToString limits recursive collection expansion"() {
        given: "a self-referential collection"
        def values = []
        values << values

        expect: "recursive collection rendering stops at depth limit"
        AotLogRuntime.safeObjectToString(values, 0, 5, 2) == "[[[DEPTH_LIMIT]]]"
    }

    def "safeArrayToString renders primitive and object arrays with truncation"() {
        when: "primitive and object arrays are rendered under element cap"
        def primitive = AotLogRuntime.safeArrayToString(([1, 2, 3, 4] as int[]), 3, 5)
        def objectArray = AotLogRuntime.safeArrayToString(([[1, 2] as int[], [3, 4] as int[]] as Object[]), 5, 5)

        then: "primitive arrays route to stable rendering and object arrays render nested arrays"
        primitive == "[1, 2, 3, ... (truncated, size=4)]"
        objectArray == "[[1, 2], [3, 4]]"
    }

    def "safeArrayToString for object arrays preserves depth for loggable elements"() {
        given: "an object array containing a loggable value"
        def values = [new LoggableValue("node")] as Object[]

        when: "array rendering is invoked below and at depth limit"
        def underLimit = AotLogRuntime.safeArrayToString(values, 0, 5, 3)
        def atLimit = AotLogRuntime.safeArrayToString(values, 3, 5, 3)

        then: "contract dispatch is used and top-level depth limit short-circuits"
        underLimit == "[loggable(node@2)]"
        atLimit == "[DEPTH_LIMIT]"
    }

    def "safeArrayToString handles null non-array and empty-cap cases"() {
        when: "different non-standard inputs are rendered"
        def nullRendered = AotLogRuntime.safeArrayToString(null, 3, 5)
        def nonArrayRendered = AotLogRuntime.safeArrayToString("value", 3, 5)
        def emptyCapRendered = AotLogRuntime.safeArrayToString(([1, 2, 3] as int[]), 0, 5)

        then: "null and non-array values are stable and cap zero truncates all elements"
        nullRendered == "null"
        nonArrayRendered == "value"
        emptyCapRendered == "[... (truncated, size=3)]"
    }

    static class CondyLoggerHolder {
        static Logger LIBPRUNUS_AOT_LOGGER
    }

    static class CondyNoFieldHolder {}

    static class CondyWrongFieldHolder {
        static String LIBPRUNUS_AOT_LOGGER
    }

    static class CondyInaccessibleHolder {
        private static Logger LIBPRUNUS_AOT_LOGGER

        static void setLogger(Logger logger) {
            LIBPRUNUS_AOT_LOGGER = logger
        }
    }

    static class CondyErrorHolder {
        static Logger LIBPRUNUS_AOT_LOGGER = initLogger()

        private static Logger initLogger() {
            throw new AssertionError("condy-fatal")
        }
    }

    static class LoggableValue implements AotLoggable {
        final String value

        LoggableValue(String value) {
            this.value = value
        }

        @Override
        void _libprunus_render(StringBuilder builder, int currentDepth) {
            builder.append("loggable(")
                    .append(value)
                    .append('@')
                    .append(currentDepth)
                    .append(')')
        }
    }

    static class NonWhitelistedValue {
        static int toStringCalls

        @Override
        String toString() {
            toStringCalls++
            "sensitive"
        }
    }

    private static final class FailingCollection extends AbstractCollection<Object> {
        private final List<Object> values

        private FailingCollection(List<Object> values) {
            this.values = values
        }

        @Override
        Iterator<Object> iterator() {
            new Iterator<Object>() {
                private int index

                @Override
                boolean hasNext() {
                    if (index == 1) {
                        throw new ConcurrentModificationException("collection-mutated")
                    }
                    index < values.size()
                }

                @Override
                Object next() {
                    values[index++]
                }
            }
        }

        @Override
        int size() {
            values.size()
        }
    }

    private static final class FailingMap extends AbstractMap<Object, Object> {
        private final Map<Object, Object> delegate

        private FailingMap(Map<Object, Object> delegate) {
            this.delegate = delegate
        }

        @Override
        Set<Map.Entry<Object, Object>> entrySet() {
            return new LinkedHashSet<Map.Entry<Object, Object>>(delegate.entrySet()) {
                @Override
                Iterator<Map.Entry<Object, Object>> iterator() {
                    def baseIterator = super.iterator()
                    return new Iterator<Map.Entry<Object, Object>>() {
                        private int index = 0

                        @Override
                        boolean hasNext() {
                            if (index == 1) {
                                throw new ConcurrentModificationException("map-mutated")
                            }
                            return baseIterator.hasNext()
                        }

                        @Override
                        Map.Entry<Object, Object> next() {
                            index++
                            return baseIterator.next()
                        }
                    }
                }
            }
        }
    }

    private enum SampleState {
        READY
    }

    private enum PolymorphicState {
        HOT {
            @Override
            String code() {
                "h"
            }
        }

        abstract String code()
    }
}
