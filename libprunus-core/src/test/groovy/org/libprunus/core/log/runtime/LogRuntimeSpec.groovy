package org.libprunus.core.log.runtime

import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import org.libprunus.core.config.CoreRuntimeConfig
import org.libprunus.core.log.annotation.MaxMessageLength
import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.TempDir

class LogRuntimeSpec extends Specification {

    interface RuntimeDiamondBase {}
    interface RuntimeDiamondLeft extends RuntimeDiamondBase {}
    interface RuntimeDiamondRight extends RuntimeDiamondBase {}
    interface RuntimeDiamondTop extends RuntimeDiamondLeft, RuntimeDiamondRight {}
    static class RuntimeDiamondImpl implements RuntimeDiamondTop {}

    interface WideBaseA {}
    interface WideBaseB {}
    interface WideJoin1 extends WideBaseA, WideBaseB {}
    interface WideJoin2 extends WideBaseA, WideBaseB {}
    interface WideJoin3 extends WideJoin1, WideJoin2 {}
    static class WideDiamondImpl implements WideJoin3 {}

    @TempDir
    Path tempDir

    def setup() {
        LogRuntimeTestSupport.resetBinding()
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 512 }
            @Override boolean isWhitelisted(Class<?> type) { return type == java.time.Instant.class }
        })
    }

    private static void writeCallsitePointer(Path root, String className) {
        def resourceDir = root.resolve("META-INF/prunus/aot")
        Files.createDirectories(resourceDir)
        Files.writeString(resourceDir.resolve("runtime-binding-callsite"), className)
    }

    def "initializeBinding on first call publishes both the binding instance and its max message length, and arms the once-only flag"() {
        given:
        LogRuntimeTestSupport.resetBinding()
        def binding = new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 1024 }
            @Override boolean isWhitelisted(Class<?> type) { return type == String.class }
        }

        when:
        LogRuntime.initializeBinding(binding)

        then:
        LogRuntime.globalConfigBinding().is(binding)
        LogRuntime.getGlobalMaxMessageLength() == binding.maxMessageLength

        when:
        LogRuntime.initializeBinding(binding)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "LogRuntime binding has already been initialized"

        and:
        LogRuntime.globalConfigBinding().is(binding)
        LogRuntime.getGlobalMaxMessageLength() == binding.maxMessageLength
    }

    def "initializeBinding rejects invalid binding and leaves bound state at DEFAULT"() {
        given:
        LogRuntimeTestSupport.resetBinding()
        def binding = bindingSupplier(this)

        when:
        LogRuntime.initializeBinding(binding)

        then:
        def ex = thrown(expectedException)
        ex.message == expectedMessage

        and:
        LogRuntime.globalConfigBinding().is(AbstractLogConfig.DEFAULT)
        LogRuntime.getGlobalMaxMessageLength() == AbstractLogConfig.DEFAULT.getMaxMessageLength()

        where:
        bindingSupplier                                            | expectedException          || expectedMessage
        ({ LogRuntimeSpec spec -> null })                          | NullPointerException       || "bindingConfig must not be null"
        ({ LogRuntimeSpec spec -> spec.bindingWithMaxLength(0) })  | IllegalArgumentException   || "binding maxMessageLength must be >= 16: 0"
        ({ LogRuntimeSpec spec -> spec.bindingWithMaxLength(
                MaxMessageLength.MAX_VALUE + 1) })                 | IllegalArgumentException   || "binding maxMessageLength must be <= 1048576: " + (MaxMessageLength.MAX_VALUE + 1)
    }

    private static AbstractLogConfig bindingWithMaxLength(int length) {
        return new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return length }
            @Override boolean isWhitelisted(Class<?> type) { return false }
        }
    }

    def "initializeBinding allows a subsequent successful call after a previous out-of-range value was rejected"() {
        given: "a freshly reset binding state plus one invalid binding and one valid binding"
        LogRuntimeTestSupport.resetBinding()
        def invalidBinding = new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 0 }
            @Override boolean isWhitelisted(Class<?> type) { return false }
        }
        def validBinding = new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 256 }
            @Override boolean isWhitelisted(Class<?> type) { return type == String.class }
        }

        when: "the invalid binding is rejected by validation and then the valid binding is supplied"
        try {
            LogRuntime.initializeBinding(invalidBinding)
        } catch (IllegalArgumentException ignored) {
        }
        LogRuntime.initializeBinding(validBinding)

        then: "the valid binding becomes the published binding — proving the once-only flag was not consumed by the prior validation failure"
        LogRuntime.globalConfigBinding().is(validBinding)

        and: "the published max length reflects the valid binding"
        LogRuntime.getGlobalMaxMessageLength() == 256

        and: "the published binding is not the DEFAULT — the second call succeeded rather than falling back"
        !LogRuntime.globalConfigBinding().is(AbstractLogConfig.DEFAULT)
    }

    def "invokeCallsiteBinding throws NullPointerException when classLoader is null"() {
        when:
        LogRuntime.invokeCallsiteBinding(null)

        then:
         def ex = thrown(NullPointerException)
        ex.message == "classLoader must not be null"
    }

    def "invokeCallsiteBinding silently returns when no callsite resource is present in classloader"() {
        given:
        def emptyLoader = new URLClassLoader([] as URL[], ClassLoader.systemClassLoader)

        when:
        LogRuntime.invokeCallsiteBinding(emptyLoader)

        then:
        noExceptionThrown()

        cleanup:
        emptyLoader.close()
    }

    def "invokeCallsiteBinding reads resource and calls bind on the referenced callsite class"() {
        given:
        writeCallsitePointer(tempDir, CallsiteProbe.name)
        CallsiteProbe.bound = false

        when:
        def loader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], getClass().classLoader)
        LogRuntime.invokeCallsiteBinding(loader)

        then:
        CallsiteProbe.bound

        cleanup:
        loader?.close()
    }

    def "invokeCallsiteBinding throws IllegalStateException when resource points to a non-existent class"() {
        given:
        writeCallsitePointer(tempDir, "org.nonexistent.MissingCallsite")

        when:
        def loader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], ClassLoader.systemClassLoader)
        LogRuntime.invokeCallsiteBinding(loader)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Failed to invoke callsite binding for target class: [org.nonexistent.MissingCallsite]"
        ex.cause instanceof ClassNotFoundException

        cleanup:
        loader?.close()
    }

    def "invokeCallsiteBinding fails fast with a project-level IllegalStateException when the callsite resource collapses to an empty class name after strip()"() {
        given: "a resource pointing to a blank class name (only whitespace)"
        writeCallsitePointer(tempDir, "   ")

        when:
        def loader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], ClassLoader.systemClassLoader)
        LogRuntime.invokeCallsiteBinding(loader)

        then: "the project-level guard short-circuits before Class.forName runs — no JDK ClassNotFoundException leaks through"
        def ex = thrown(IllegalStateException)
        ex.message.contains("is empty or blank")
        ex.message.contains(LogRuntime.CALLSITE_BINDING_RESOURCE)

        and: "the exception carries no cause — the guard raised it directly, not as a wrapper around a downstream JDK failure"
        ex.cause == null

        cleanup:
        loader?.close()
    }

    def "invokeCallsiteBinding fails fast with a project-level IllegalStateException when the callsite resource is exactly zero bytes long"() {
        given: "a resource file written with zero bytes — simulates a build tool emitting an empty META-INF pointer"
        def resourceDir = tempDir.resolve("META-INF/prunus/aot")
        Files.createDirectories(resourceDir)
        Files.write(resourceDir.resolve("runtime-binding-callsite"), new byte[0])

        when:
        def loader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], ClassLoader.systemClassLoader)
        LogRuntime.invokeCallsiteBinding(loader)

        then: "the project-level guard short-circuits the 0-byte deployment accident before it reaches Class.forName"
        def ex = thrown(IllegalStateException)
        ex.message.contains("is empty or blank")
        ex.message.contains(LogRuntime.CALLSITE_BINDING_RESOURCE)

        and: "the guard raised the exception itself — no wrapped CNFE / IOException from Class.forName"
        ex.cause == null

        cleanup:
        loader?.close()
    }

    def "invokeCallsiteBinding called twice with publishing probes surfaces the once-only guard from initializeBinding on the second call"() {
        given: "a freshly reset binding and a callsite pointer to a probe that actually performs initializeBinding on bind()"
        LogRuntimeTestSupport.resetBinding()
        writeCallsitePointer(tempDir, BindingPublishingProbe.name)
        BindingPublishingProbe.invokeCount = 0
        def loader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], getClass().classLoader)

        when: "invokeCallsiteBinding is called once and succeeds"
        LogRuntime.invokeCallsiteBinding(loader)

        then: "the probe's bind() succeeded — initializeBinding consumed the once-only slot"
        BindingPublishingProbe.invokeCount == 1

        when: "invokeCallsiteBinding is called a second time on the same loader; the probe's bind() will re-invoke initializeBinding"
        LogRuntime.invokeCallsiteBinding(loader)

        then: "the wrapping IllegalStateException carries the target-class tail in its message"
        def ex = thrown(IllegalStateException)
        ex.message == "Failed to invoke callsite binding for target class: [" + BindingPublishingProbe.name + "]"

        and: "the wrapped cause is the once-only IllegalStateException raised inside initializeBinding"
        ex.cause instanceof IllegalStateException
        ex.cause.message == "LogRuntime binding has already been initialized"

        cleanup:
        loader?.close()
    }

    def "invokeCallsiteBinding wraps ExceptionInInitializerError from failing clinit in IllegalStateException"() {
        given:
        def fakeClassName = "org.libprunus.test.ClinitFailCallsite"
        writeCallsitePointer(tempDir, fakeClassName)

        when:
        def loader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], ClassLoader.systemClassLoader) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name == fakeClassName) {
                    throw new ExceptionInInitializerError(new RuntimeException("simulated clinit failure"))
                }
                return super.findClass(name)
            }
        }
        LogRuntime.invokeCallsiteBinding(loader)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Failed to invoke callsite binding for target class: [org.libprunus.test.ClinitFailCallsite]"
        ex.cause instanceof ExceptionInInitializerError

        cleanup:
        loader?.close()
    }

    def "invokeCallsiteBinding unwraps InvocationTargetException cause when bind() throws"() {
        given:
        writeCallsitePointer(tempDir, BindThrowingProbe.name)

        when:
        def loader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], getClass().classLoader)
        LogRuntime.invokeCallsiteBinding(loader)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("for target class: [" + BindThrowingProbe.name + "]")
        ex.cause instanceof RuntimeException
        ex.cause.message == "invoked-bind-fail"
        !(ex.cause instanceof InvocationTargetException)

        cleanup:
        loader?.close()
    }

    def "invokeCallsiteBinding wraps IOException from resource read in IllegalStateException with target class info absent"() {
        given: "a classloader whose callsite resource stream throws IOException on read, before the class name can be parsed"
        def loader = new ClassLoader(ClassLoader.systemClassLoader) {
            @Override
            InputStream getResourceAsStream(String name) {
                if (name == LogRuntime.CALLSITE_BINDING_RESOURCE) {
                    return new InputStream() {
                        @Override
                        int read() throws IOException {
                            throw new IOException("simulated read failure")
                        }
                    }
                }
                return super.getResourceAsStream(name)
            }
        }

        when:
        LogRuntime.invokeCallsiteBinding(loader)

        then: "the IOException is wrapped in the project-level IllegalStateException, with no target-class tail because callsiteClass was never parsed"
        def ex = thrown(IllegalStateException)
        ex.message == "Failed to invoke callsite binding"
        ex.cause instanceof IOException
        ex.cause.message == "simulated read failure"
    }

    def "invokeCallsiteBinding only loads the first matching resource when two classpath roots provide the same resource path"() {
        given:
        def firstRoot = Files.createDirectory(tempDir.resolve("first"))
        def secondRoot = Files.createDirectory(tempDir.resolve("second"))
        writeCallsitePointer(firstRoot, CallsiteProbe.name)
        writeCallsitePointer(secondRoot, "org.nonexistent.ShouldNeverLoad")
        CallsiteProbe.bound = false

        when:
        def loader = new URLClassLoader(
                [firstRoot.toUri().toURL(), secondRoot.toUri().toURL()] as URL[],
                getClass().classLoader)
        LogRuntime.invokeCallsiteBinding(loader)

        then:
        CallsiteProbe.bound
        noExceptionThrown()

        cleanup:
        loader?.close()
    }

    def "invokeCallsiteBinding called twice sequentially invokes bind both times without throwing"() {
        given:
        writeCallsitePointer(tempDir, CallsiteProbe.name)
        def loader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], getClass().classLoader)

        when:
        CallsiteProbe.bound = false
        LogRuntime.invokeCallsiteBinding(loader)

        then:
        CallsiteProbe.bound

        when:
        CallsiteProbe.bound = false
        LogRuntime.invokeCallsiteBinding(loader)

        then:
        CallsiteProbe.bound

        cleanup:
        loader?.close()
    }

    def "invokeCallsiteBinding does not touch data-plane reference even when bind() succeeds"() {
        given: "a freshly reset runtime with a known data-plane reference holding disabled config"
        LogRuntimeTestSupport.resetBinding()
        def dataPlaneRef = new AtomicReference<CoreRuntimeConfig>(
                new CoreRuntimeConfig(new LogRuntimeConfig(false)))
        LogRuntime.linkToDataPlane(dataPlaneRef)
        writeCallsitePointer(tempDir, CallsiteProbe.name)
        CallsiteProbe.bound = false

        when:
        def loader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], getClass().classLoader)
        LogRuntime.invokeCallsiteBinding(loader)

        then: "callsite's bind() was invoked"
        CallsiteProbe.bound

        and: "data-plane reference is untouched — isEnabled() still observes the disabled config installed before invokeCallsiteBinding"
        !LogRuntime.isEnabled()
        !dataPlaneRef.get().log().enabled()

        cleanup:
        loader?.close()
    }

    def "getGlobalMaxMessageLength is unaffected by linkToDataPlane and config refresh"() {
        given:
        LogRuntimeTestSupport.resetBinding()
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 768 }
            @Override boolean isWhitelisted(Class<?> type) { return false }
        })
        def configRef = new AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(true)))
        LogRuntime.linkToDataPlane(configRef)

        when:
        configRef.set(new CoreRuntimeConfig(new LogRuntimeConfig(false)))

        then:
        LogRuntime.getGlobalMaxMessageLength() == 768
    }

    def "linkToDataPlane repeated replacement leaves compile-time binding fields and once-only flag untouched"() {
        given:
        LogRuntimeTestSupport.resetBinding()
        def probeBinding = new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 768 }
            @Override boolean isWhitelisted(Class<?> type) { return type == String.class }
        }
        LogRuntime.initializeBinding(probeBinding)
        def refA = new AtomicReference<CoreRuntimeConfig>(new CoreRuntimeConfig(new LogRuntimeConfig(true)))
        def refB = new AtomicReference<CoreRuntimeConfig>(new CoreRuntimeConfig(new LogRuntimeConfig(false)))

        when:
        LogRuntime.linkToDataPlane(refA)
        LogRuntime.linkToDataPlane(refB)

        then: "compile-time binding fields remain the publisher set in initializeBinding"
        LogRuntime.globalConfigBinding().is(probeBinding)
        LogRuntime.getGlobalMaxMessageLength() == 768

        when: "another initializeBinding call still surfaces the once-only guard — the data-plane swaps did not reset bindingInitialized"
        LogRuntime.initializeBinding(probeBinding)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "LogRuntime binding has already been initialized"
    }

    def "linkToDataPlane installs and replaces live reference, propagating set() updates from whichever reference is currently installed"() {
        given: "a first AtomicReference holding a disabled config"
        def refA = new AtomicReference<CoreRuntimeConfig>(new CoreRuntimeConfig(new LogRuntimeConfig(false)))

        when: "refA is installed as the data-plane reference"
        LogRuntime.linkToDataPlane(refA)

        then: "isEnabled() observes refA's current value"
        !LogRuntime.isEnabled()

        when: "refA's value is hot-swapped to enabled"
        refA.set(new CoreRuntimeConfig(new LogRuntimeConfig(true)))

        then: "isEnabled() reflects the in-place update through refA"
        LogRuntime.isEnabled()

        when: "a second AtomicReference holding a disabled config replaces refA"
        def refB = new AtomicReference<CoreRuntimeConfig>(new CoreRuntimeConfig(new LogRuntimeConfig(false)))
        LogRuntime.linkToDataPlane(refB)

        then: "isEnabled() now observes refB"
        !LogRuntime.isEnabled()

        when: "the now-detached refA is mutated to enabled"
        refA.set(new CoreRuntimeConfig(new LogRuntimeConfig(true)))

        then: "isEnabled() is unaffected — refA is no longer the installed reference"
        !LogRuntime.isEnabled()

        when: "refB itself is hot-swapped to enabled"
        refB.set(new CoreRuntimeConfig(new LogRuntimeConfig(true)))

        then: "isEnabled() reflects refB's new value"
        LogRuntime.isEnabled()
    }

    def "linkToDataPlane null input preserves previously installed AtomicReference"() {
        given: "a known AtomicReference is installed via a successful linkToDataPlane call"
        def installedRef = new AtomicReference<CoreRuntimeConfig>(
                new CoreRuntimeConfig(new LogRuntimeConfig(true)))
        LogRuntime.linkToDataPlane(installedRef)

        when: "linkToDataPlane is invoked with null"
        LogRuntime.linkToDataPlane(null)

        then: "NullPointerException with the project message propagates"
        def ex = thrown(NullPointerException)
        ex.message == "configRef must not be null"

        and: "the previously installed reference is still the active data-plane reference — proving requireNonNull runs before the field assignment"
        LogRuntime.isEnabled()
        installedRef.get().log().enabled()
    }

    def "condyLoggerFactory selects logger source by ownerName presence and ignores the condy placeholder name and type parameters"() {
        given:
        def lookup = MethodHandles.privateLookupIn(
                CondyNoFieldHolder,
                MethodHandles.lookup())

        when:
        def logger = LogRuntime.condyLoggerFactory(lookup, name, loggerType, ownerName)

        then:
        logger.name == expectedName

        where: "ownerName covers real / null / blank variants; name and type cells flip across rows to prove they do not influence resolution"
        ownerName              | name      | loggerType   || expectedName
        "sample.runtime.Owner" | "name"    | Logger.class || "sample.runtime.Owner"
        "sample.runtime.Owner" | "alpha"   | Integer.class|| "sample.runtime.Owner"
        "   "                  | "name"    | Logger.class || CondyNoFieldHolder.name
        null                   | "name"    | Logger.class || CondyNoFieldHolder.name
        ""                     | "name"    | Logger.class || CondyNoFieldHolder.name
        "\t"                   | "name"    | Logger.class || CondyNoFieldHolder.name
        "\n  "                 | "name"    | Logger.class || CondyNoFieldHolder.name
        null                   | "alpha"   | Integer.class|| CondyNoFieldHolder.name
        null                   | "beta"    | String.class || CondyNoFieldHolder.name
        "  "                   | "alpha"   | Integer.class|| CondyNoFieldHolder.name
        "  "                   | "beta"    | String.class || CondyNoFieldHolder.name
    }

    def "isWhitelistedCached returns expected result based on class hierarchy and exact name match"() {
        expect:
        LogRuntime.isWhitelistedCached(type, LogRuntime.buildWhitelistCache(whitelist)) == expected

        where:
        type                | whitelist                                                || expected
        null                | ["java.lang.String"] as String[]                         || false
        String.class        | [] as String[]                                           || false
        Object.class        | [] as String[]                                           || false
        Comparable.class    | [] as String[]                                           || false
        RuntimeDiamondImpl  | [] as String[]                                           || false
        String.class        | ["java.lang.Integer"] as String[]                        || false
        String.class        | ["java.lang.String"] as String[]                         || true
        String.class        | ["java.lang.Integer", "java.lang.String"] as String[]    || true
        Integer.class       | ["java.lang.Number"] as String[]                         || true
        String.class        | ["java.lang.Comparable"] as String[]                     || true
        String.class        | ["java.lang.CharSequence"] as String[]                   || true
        StringBuilder       | ["java.lang.CharSequence"] as String[]                   || true
        Integer.class       | ["java.lang.CharSequence"] as String[]                   || false
        Long.class          | ["java.lang.Number"] as String[]                         || true
        String.class        | ["java.lang.Number"] as String[]                         || false
        String.class        | ["java.lang.Object"] as String[]                         || true
        LocalDate           | ["java.time.temporal.TemporalAccessor"] as String[]      || true
        LocalDateTime       | ["java.time.temporal.TemporalAccessor"] as String[]      || true
        String.class        | ["java.time.temporal.TemporalAccessor"] as String[]      || false
    }

    def "isWhitelistedCached at runtime boundary resolves diamond interface hierarchy and remains stable across repeated calls"() {
        when:
        def cache = LogRuntime.buildWhitelistCache(whitelist)
        def first = LogRuntime.isWhitelistedCached(RuntimeDiamondImpl, cache)
        def second = LogRuntime.isWhitelistedCached(RuntimeDiamondImpl, cache)

        then:
        first == expected
        second == expected

        where:
        whitelist                                                     || expected
        [RuntimeDiamondBase.name] as String[]                         || true
        [RuntimeDiamondLeft.name] as String[]                         || true
        [RuntimeDiamondRight.name] as String[]                        || true
        [RuntimeDiamondTop.name] as String[]                          || true
        ["no.such.Class"] as String[]                                || false
        ["no.such.Class", RuntimeDiamondBase.name] as String[]       || true
    }

    def "isWhitelistedCached resolves wide diamond DAG deterministically across repeated invocations when multiple join interfaces share the same ancestors"() {
        when:
        def cache = LogRuntime.buildWhitelistCache(whitelist)
        def first = LogRuntime.isWhitelistedCached(WideDiamondImpl, cache)
        def second = LogRuntime.isWhitelistedCached(WideDiamondImpl, cache)

        then:
        first == expected
        second == expected

        where:
        whitelist                                                          || expected
        [WideBaseA.name] as String[]                                       || true
        [WideBaseB.name] as String[]                                       || true
        [WideJoin1.name] as String[]                                       || true
        [WideJoin2.name] as String[]                                       || true
        ["no.such.Class"] as String[]                                      || false
        ["no.such.Class", WideBaseA.name] as String[]                      || true
    }

    def "isWhitelistedCached resolves matches when probed type is itself an interface (no superclass branch)"() {
        expect:
        LogRuntime.isWhitelistedCached(probeType, LogRuntime.buildWhitelistCache(whitelist)) == expected

        where: "interface probes have null superclass; the algorithm must still classify them by interface graph or exact name without NPE"
        probeType                || whitelist                                  || expected
        Comparable               || ["java.lang.Comparable"] as String[]       || true
        Comparable               || ["java.lang.Object"] as String[]           || false
        java.io.Serializable     || ["java.io.Serializable"] as String[]       || true
        RuntimeDiamondTop        || [RuntimeDiamondBase.name] as String[]      || true
        RuntimeDiamondTop        || ["no.such.Class"] as String[]              || false
    }

    def "isWhitelistedCached invokes ClassValue#computeValue at most once per Class"() {
        given: "a counting ClassValue<Boolean> that records each computeValue invocation"
        def computeCounts = new java.util.concurrent.ConcurrentHashMap<Class<?>, java.util.concurrent.atomic.AtomicInteger>()
        def countingCache = new ClassValue<Boolean>() {
            @Override
            protected Boolean computeValue(Class<?> type) {
                computeCounts.computeIfAbsent(type, { _ -> new java.util.concurrent.atomic.AtomicInteger() }).incrementAndGet()
                return type == String.class
            }
        }

        when: "isWhitelistedCached is called many times for the same Class"
        def stringResults = (1..5).collect { LogRuntime.isWhitelistedCached(String.class, countingCache) }
        def integerResults = (1..3).collect { LogRuntime.isWhitelistedCached(Integer.class, countingCache) }

        then: "each repeat invocation returns the same memoised result"
        stringResults.every { it == true }
        integerResults.every { it == false }

        and: "computeValue ran exactly once per Class — confirming the zero-overhead repeated-read guarantee"
        computeCounts.get(String.class).get() == 1
        computeCounts.get(Integer.class).get() == 1
    }

    def "buildWhitelistCache rejects duplicate FQCN entries with a project-level IllegalArgumentException that embeds the offending name in the message"() {
        when:
        LogRuntime.buildWhitelistCache([
                "java.lang.String",
                "java.lang.Number",
                "java.lang.String",
        ] as String[])

        then: "the guard fires the IAE itself, not the JDK Set.of duplicate-element NPE; the offending FQCN is embedded for diagnostic locality"
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("duplicate")
        ex.message.contains("java.lang.String")
    }

    def "buildWhitelistCache rejects a null entry inside the whitelist array with a project-level NullPointerException carrying a descriptive message"() {
        when:
        LogRuntime.buildWhitelistCache([
                "java.lang.String",
                null,
                "java.lang.Number",
        ] as String[])

        then: "the guard fires the project-level NPE itself, not the JDK Set.of null-element NPE; the message describes the contract"
        def ex = thrown(NullPointerException)
        ex.message.contains("whitelistNames must not contain null entries")
    }

    def "buildWhitelistCache rejects a null whitelistNames array with a project-level NullPointerException"() {
        when:
        LogRuntime.buildWhitelistCache((String[]) null)

        then:
        def ex = thrown(NullPointerException)
        ex.message.contains("whitelistNames must not be null")
    }

    def "buildWhitelistCache returns a ClassValue whose results for distinct types are independent"() {
        given: "a single cache built from the Number whitelist"
        def cache = LogRuntime.buildWhitelistCache([Number.name] as String[])

        expect: "Integer is recognised through its superclass chain"
        cache.get(Integer) == true

        and: "Long is independently recognised through the same cache"
        cache.get(Long) == true

        and: "String is not recognised — no false positive bled across through the cache"
        cache.get(String) == false

        and: "a repeated lookup of Integer remains true — the cache is idempotent and does not degrade between reads"
        cache.get(Integer) == true

        and: "a repeated lookup of String remains false — the cache did not silently flip a memoized negative into a positive after the prior positive reads"
        cache.get(String) == false
    }

    def "linkToDataPlane refresh does not pollute compile-time binding state"() {
        given: "a data-plane reference installed alongside the compile-time binding configured in setup()"
        def dataPlaneRef = new AtomicReference<CoreRuntimeConfig>(
                new CoreRuntimeConfig(new LogRuntimeConfig(true)))
        LogRuntime.linkToDataPlane(dataPlaneRef)

        when: "the data plane is hot-swapped to a disabled config"
        dataPlaneRef.set(new CoreRuntimeConfig(new LogRuntimeConfig(false)))

        then: "compile-time binding state remains unchanged (whitelist used here as an observable compile-time probe)"
        LogRuntime.globalConfigBinding().isWhitelisted(java.time.Instant)
        !LogRuntime.globalConfigBinding().isWhitelisted(String)

        and: "the data-plane flip is observable via isEnabled() — proving the data-plane mutation actually happened"
        !LogRuntime.isEnabled()
    }

    static class CondyNoFieldHolder {}

    static class CallsiteProbe {
        static volatile boolean bound = false
        static void bind() { bound = true }
    }

    static class BindThrowingProbe {
        static void bind() {
            throw new RuntimeException("invoked-bind-fail")
        }
    }

    static class BindingPublishingProbe {
        static volatile int invokeCount = 0

        static void bind() {
            invokeCount++
            LogRuntime.initializeBinding(new AbstractLogConfig() {
                @Override int getMaxMessageLength() { return 512 }
                @Override boolean isWhitelisted(Class<?> type) { return false }
            })
        }
    }
}
