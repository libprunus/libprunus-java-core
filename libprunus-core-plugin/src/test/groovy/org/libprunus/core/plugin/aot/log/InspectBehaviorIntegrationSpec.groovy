package org.libprunus.core.plugin.aot.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.read.ListAppender
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.ClassWriter
import net.bytebuddy.jar.asm.ConstantDynamic
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.pool.TypePool
import org.libprunus.core.config.CoreRuntimeConfig
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.log.annotation.MethodLoggingProfile
import org.libprunus.core.log.runtime.AbstractLogConfig
import org.libprunus.core.log.runtime.LogRuntime
import org.libprunus.core.log.runtime.LogRuntimeConfig
import org.libprunus.core.plugin.aot.AotCompileContext
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectBoundedGenericService
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectClassAnnotatedService
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectFilterService
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectGenericService
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectIgnoredClassService
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectIgnoredFieldDto
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectMaskedChildDto
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectMaskedService
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectMixedMaskDto
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectMultiReturnService
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectOverloadService
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectOverriddenMaskChildDto
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectPortAdapter
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectRedundantService
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectRegistry
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectThrowingService
import org.libprunus.core.plugin.aot.log.fixture.svc.ConcreteChainSvc
import org.slf4j.Logger
import spock.lang.Specification

class InspectBehaviorIntegrationSpec extends Specification {

    def setupSpec() {
        try {
            LogRuntime.initializeBinding(new AbstractLogConfig() {
                @Override int getMaxMessageLength() { return 512 }
                @Override boolean isWhitelisted(Class<?> type) { return false }
            })
        } catch (IllegalStateException ignored) {}
        LogRuntime.linkToDataPlane(new java.util.concurrent.atomic.AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(true))))
    }

    def setup() {
        LogRuntime.@boundMaxMessageLength = 512
        LogRuntime.linkToDataPlane(new java.util.concurrent.atomic.AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(true))))
    }

    // ── InspectMaskedService.lookup: @DoNotLog on id param ──

    def "lookup enter log contains fallback value and omits id param annotated with ignore"() {
        given:
        def bytes = transformWithRegistry(InspectMaskedService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMaskedService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."lookup"(null, "fallback-value")

        then:
        result == "fallback-value"

        and:
        def enterMsg = filterMethodLogs(appender, "lookup").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMaskedService.lookup(fallback=fallback-value)"

        and:
        def exitMsg = filterMethodLogs(appender, "lookup").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectMaskedService.lookup(value=fallback-value)"

        cleanup:
        detachAppender(appender)
    }

    // ── InspectMaskedService.transfer: method-level @Sensitive masks all params and return ──

    def "transfer enter and exit logs mask all params and return value due to method-level @Sensitive"() {
        given:
        def bytes = transformWithRegistry(InspectMaskedService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMaskedService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."transfer"("Alice", "Bob")

        then:
        result == "Alice->Bob"

        and:
        def enterMsg = filterMethodLogs(appender, "transfer").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMaskedService.transfer(from=***, to=***)"

        and:
        def exitMsg = filterMethodLogs(appender, "transfer").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectMaskedService.transfer(value=***)"

        cleanup:
        detachAppender(appender)
    }

    // ── InspectMaskedService.rank: mixed primitive + reference params, enter log format ──

    def "rank enter and exit logs render primitive and reference params correctly"() {
        given:
        def bytes = transformWithRegistry(InspectMaskedService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMaskedService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."rank"(42, "high")

        then:
        result == "high-42"

        and:
        def enterMsg = filterMethodLogs(appender, "rank").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMaskedService.rank(score=42, label=high)"

        and:
        def exitMsg = filterMethodLogs(appender, "rank").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectMaskedService.rank(value=high-42)"

        cleanup:
        detachAppender(appender)
    }

    // ── InspectMaskedService.describe: @DoNotLog on method (return) with @Sensitive on param ──

    def "describe enter log masks the id param while exit log omits the return value due to @DoNotLog on method"() {
        given:
        def bytes = transformWithRegistry(InspectMaskedService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMaskedService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."describe"("secret")

        then:
        result == "id=secret"

        and:
        def enterMsg = filterMethodLogs(appender, "describe").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMaskedService.describe(id=***)"

        and:
        def exitMsg = filterMethodLogs(appender, "describe").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectMaskedService.describe()"

        cleanup:
        detachAppender(appender)
    }

    // ── InspectPortAdapter.fetch: annotation inheritance + DEBUG level + field extractor ──

    def "fetch enter and exit logs follow inherited masking and ignore annotations from InspectPort interface"() {
        given:
        def bytes = transformWithRegistry(InspectPortAdapter, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectPortAdapter.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."fetch"("secret-key", "context-val")

        then:
        result == "secret-key:context-val"

        and:
        def enterMsg = filterMethodLogs(appender, "fetch").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectPortAdapter.fetch(context=***)"

        and:
        def exitMsg = filterMethodLogs(appender, "fetch").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectPortAdapter.fetch(value=***)"

        cleanup:
        detachAppender(appender)
    }

    def "fetch emits both enter and exit events at DEBUG level as declared by the Adapter profile"() {
        given:
        def bytes = transformWithRegistry(InspectPortAdapter, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectPortAdapter.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."fetch"("secret-key", "context-val")

        then:
        def events = appender.list.findAll { it.formattedMessage.contains(".fetch(") }
        events.size() == 2
        events.every { it.level == Level.DEBUG }
        !events.any { it.level == Level.INFO }

        cleanup:
        detachAppender(appender)
    }

    def "fetch attaches traceId structured key-value pair injected by the field extractor to every log event"() {
        given:
        def bytes = transformWithRegistry(InspectPortAdapter, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectPortAdapter.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."fetch"("secret-key", "context-val")

        then:
        def events = appender.list.findAll { it.formattedMessage.contains(".fetch(") }
        events.size() == 2
        events.every { e -> e.keyValuePairs.any { it.key == "traceId" && it.value == "inspect-trace" } }

        cleanup:
        detachAppender(appender)
    }

    // ── InspectRedundantService.resolve: redundant parent interface re-declaration (BFS pruning) ──

    def "transform of class with redundant interface re-declaration does not throw and applies child interface ALL masking"() {
        given:
        def bytes = transformWithRegistry(InspectRedundantService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectRedundantService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."resolve"("secret-key")

        then:
        result == "secret-key"

        and:
        def enterMsg = filterMethodLogs(appender, "resolve").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectRedundantService.resolve(key=***)"

        and:
        def exitMsg = filterMethodLogs(appender, "resolve").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectRedundantService.resolve(value=***)"

        cleanup:
        detachAppender(appender)
    }

    // ── InspectGenericService.process: generic ancestor annotation inheritance end-to-end ──

    def "transform of generic interface specialization inherits ancestor @Sensitive and masks the input parameter"() {
        given:
        def bytes = transformWithRegistry(InspectGenericService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectGenericService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."process"("sensitive-value")

        then:
        result == "sensitive-value"

        and:
        def enterMsg = filterMethodLogs(appender, "process").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectGenericService.process(input=***)"

        and:
        def exitMsg = filterMethodLogs(appender, "process").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectGenericService.process(value=***)"

        cleanup:
        detachAppender(appender)
    }

    // ── InspectBoundedGenericService.normalize: declaredMethod fallback path when T equals bound type ──
    // T extends CharSequence and T=CharSequence matches erasure; @Sensitive on the declared ancestor method
    // is located by the declaredMethod fallback path in resolveEffectiveParamAnnotation.

    def "bounded generic specialization with T equal to its bound inherits method-level @Sensitive and masks the param via declaredMethod fallback"() {
        given:
        def bytes = transformWithRegistry(InspectBoundedGenericService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectBoundedGenericService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."normalize"("sensitive-data")

        then:
        result == "sensitive-data"

        and:
        def enterMsg = filterMethodLogs(appender, "normalize").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectBoundedGenericService.normalize(input=***)"

        and:
        def exitMsg = filterMethodLogs(appender, "normalize").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectBoundedGenericService.normalize(value=***)"

        cleanup:
        detachAppender(appender)
    }

    // ── InspectMaskedChildDto: subclass-declared field is not affected by parent class-level @Sensitive ──

    def "toString on InspectMaskedChildDto renders childSecret in plain text because parent class-level @Sensitive does not propagate to subclass-declared fields"() {
        given:
        def bytes = transformWithRegistry(InspectMaskedChildDto, InspectRegistry)
        def loaded = loadFresh(bytes, InspectMaskedChildDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        loaded.getField("childSecret").set(instance, "sensitive-data")

        when:
        def result = instance.toString()

        then:
        result == "InspectMaskedChildDto(childSecret=sensitive-data, parentSecret=***)"
    }

    // ── InspectClassAnnotatedService: class-level @Sensitive as default for all method params and return ──

    def "greet enter and exit logs mask all params and return value due to class-level @Sensitive"() {
        given:
        def bytes = transformWithRegistry(InspectClassAnnotatedService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectClassAnnotatedService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."greet"("Alice", "Hello")

        then:
        result == "Hello, Alice"

        and:
        def enterMsg = filterMethodLogs(appender, "greet").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectClassAnnotatedService.greet(name=***, greeting=***)"

        and:
        def exitMsg = filterMethodLogs(appender, "greet").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectClassAnnotatedService.greet(value=***)"

        cleanup:
        detachAppender(appender)
    }

    // ── InspectMixedMaskDto: field-level @DoLog overrides class-level @Sensitive ──

    def "toString on InspectMixedMaskDto masks the unannotated field and renders the @DoLog field in plain text"() {
        given:
        def bytes = transformWithRegistry(InspectMixedMaskDto, InspectRegistry)
        def loaded = loadFresh(bytes, InspectMixedMaskDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        loaded.getField("masked").set(instance, "sensitive")
        loaded.getField("unmasked").set(instance, "public-value")

        when:
        def result = instance.toString()

        then:
        result == "InspectMixedMaskDto(masked=***, unmasked=public-value)"
    }

    // ── InspectOverriddenMaskChildDto: child @DoLog means child fields are plain, parent fields remain masked ──

    def "toString on InspectOverriddenMaskChildDto renders childField in plain text and still masks parentSecret from the @Sensitive-annotated parent"() {
        given:
        def bytes = transformWithRegistry(InspectOverriddenMaskChildDto, InspectRegistry)
        def loaded = loadFresh(bytes, InspectOverriddenMaskChildDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        loaded.getField("childField").set(instance, "child-value")
        loaded.getSuperclass().getField("parentSecret").set(instance, "parent-sensitive")

        when:
        def result = instance.toString()

        then:
        result == "InspectOverriddenMaskChildDto(childField=child-value, parentSecret=***)"
    }

    // ── InspectIgnoredFieldDto: @DoNotLog on field excludes it entirely from toString ──

    def "toString on InspectIgnoredFieldDto omits the field annotated with @DoNotLog entirely"() {
        given:
        def bytes = transformWithRegistry(InspectIgnoredFieldDto, InspectRegistry)
        def loaded = loadFresh(bytes, InspectIgnoredFieldDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        loaded.getField("visible").set(instance, "show-this")
        loaded.getField("hidden").set(instance, "never-show")

        when:
        def result = instance.toString()

        then:
        result == "InspectIgnoredFieldDto(visible=show-this)"
    }

    // ── LightweightInjectionVisitor: exception escape -- intentional design ──

    def "instrumented method propagates RuntimeException unchanged and emits no exit log"() {
        given:
        def bytes = transformWithRegistry(InspectThrowingService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectThrowingService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."process"("some-input")

        then:
        def ex = thrown(RuntimeException)
        ex.message == "business-error"

        and:
        def enterMsg = filterMethodLogs(appender, "process").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectThrowingService.process(input=some-input)"

        and:
        !filterMethodLogs(appender, "process").any { it.contains("[EXIT]") }

        cleanup:
        detachAppender(appender)
    }

    // ── ConcreteChainSvc: abstract-class 3-level inheritance, class-level @Sensitive ──

    def "ConcreteChainSvc.fetch masks query param and return value due to class-level @Sensitive on AbstractChainSvc, and business return value is not corrupted"() {
        given:
        def bytes = transformWithRegistry(ConcreteChainSvc, SvcRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, ConcreteChainSvc.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."fetch"("hello")

        then:
        result == "result:hello"

        and:
        def enterMsg = filterMethodLogs(appender, "fetch").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] ConcreteChainSvc.fetch(query=***)"

        and:
        def exitMsg = filterMethodLogs(appender, "fetch").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] ConcreteChainSvc.fetch(value=***)"

        cleanup:
        detachAppender(appender)
    }

    // ── InspectMultiReturnService: method logic integrity across varied return paths ──

    def "classify preserves each of its four return paths after instrumentation, and always masks the input param"() {
        given:
        def bytes = transformWithRegistry(InspectMultiReturnService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMultiReturnService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."classify"(input)

        then:
        result == expected

        and:
        def enterMsg = filterMethodLogs(appender, "classify").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMultiReturnService.classify(input=***)"

        and:
        def exitMsg = filterMethodLogs(appender, "classify").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectMultiReturnService.classify(value=${expected})"

        cleanup:
        detachAppender(appender)

        where:
        input   | expected
        null    | "null"
        ""      | "empty"
        "hi"    | "short"
        "hello" | "long"
    }

    def "firstIndex returns correct value from in-loop and end-of-loop exit paths after instrumentation, and omits the ignored target param from enter log"() {
        given:
        def bytes = transformWithRegistry(InspectMultiReturnService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMultiReturnService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def foundResult = instance."firstIndex"(["a", "b", "c"], "b")

        then:
        foundResult == 1

        and:
        def enterMsg = filterMethodLogs(appender, "firstIndex").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMultiReturnService.firstIndex(items=[a, b, c])"

        and:
        def exitMsg = filterMethodLogs(appender, "firstIndex").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectMultiReturnService.firstIndex(value=1)"

        cleanup:
        detachAppender(appender)
    }

    def "firstIndex returns -1 from end-of-loop fallback path and from null-guard early exit after instrumentation"() {
        given:
        def bytes = transformWithRegistry(InspectMultiReturnService, InspectRegistry)
        def loaded = loadFresh(bytes, InspectMultiReturnService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        expect:
        instance."firstIndex"(["a", "b", "c"], "z") == -1

        and:
        instance."firstIndex"(null, "b") == -1
    }

    def "multiply returns correct value from early-exit-on-zero and normal-multiplication paths after instrumentation"() {
        given:
        def bytes = transformWithRegistry(InspectMultiReturnService, InspectRegistry)
        def loaded = loadFresh(bytes, InspectMultiReturnService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        expect:
        instance."multiply"(0, 99) == 0
        instance."multiply"(5, 0) == 0

        and:
        instance."multiply"(3, 4) == 12
        instance."multiply"(-2, 7) == -14
    }

    def "record void method completes without exception and emits enter with masked params plus exit with no return value"() {
        given:
        def bytes = transformWithRegistry(InspectMultiReturnService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMultiReturnService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."record"("k", "v")

        then:
        noExceptionThrown()

        and:
        def enterMsg = filterMethodLogs(appender, "record").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMultiReturnService.record(key=***, value=***)"

        and:
        def exitMsg = filterMethodLogs(appender, "record").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectMultiReturnService.record()"

        cleanup:
        detachAppender(appender)
    }

    // ── InspectMultiReturnService.accumulate: long param shifts firstLocal, IINC + LSTORE/LLOAD must use shifted slots ──
    // Long parameter forces firstLocal=4; shiftAmount=3 shifts local result(long) and i(int).

    def "accumulate logs exact enter and exit for long param and returns correct result after instrumentation"() {
        given:
        def bytes = transformWithRegistry(InspectMultiReturnService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMultiReturnService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        // odd(i=1) then even(i=2): base - 1 + 2 = base + 1.
        when:
        def result = instance."accumulate"(10L, 2)

        then:
        result == 11L

        and:
        def enterMsg = filterMethodLogs(appender, "accumulate").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMultiReturnService.accumulate(base=10, steps=2)"

        and:
        def exitMsg = filterMethodLogs(appender, "accumulate").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectMultiReturnService.accumulate(value=11)"

        cleanup:
        detachAppender(appender)
    }

    def "accumulate returns correct long value from each branch combination after instrumentation, confirming LVT shifts for IINC and LSTORE are applied correctly"() {
        given:
        def bytes = transformWithRegistry(InspectMultiReturnService, InspectRegistry)
        def loaded = loadFresh(bytes, InspectMultiReturnService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        expect:
        instance."accumulate"(base, steps) == expected

        where:
        base | steps | expected
        10L  | 0     | 10L    // no iterations -- base returned unchanged
        10L  | 1     | 9L     // odd only (i=1): base - 1
        10L  | 2     | 11L    // odd then even (i=1,2): base - 1 + 2
        10L  | 3     | 10L    // odd, even, odd (i=1,2,3): base - 1 + 2 - 1
        10L  | 4     | 14L    // odd, even, odd, even (i=1,2,3,4): base - 1 + 2 - 1 + 4
    }

    // ── InspectMultiReturnService.weighted: DSTORE/DLOAD on local double, IINC on local int ──
    // Int params keep firstLocal=3; shiftAmount=3 shifts local double acc to slots 6-7 and int i to slot 8.

    def "weighted returns correct double from odd and even branches after instrumentation, confirming DSTORE/DLOAD LVT slots are shifted correctly"() {
        given:
        def bytes = transformWithRegistry(InspectMultiReturnService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMultiReturnService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."weighted"(base, steps)

        then:
        result == expected

        and:
        def enterMsg = filterMethodLogs(appender, "weighted").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMultiReturnService.weighted(base=${base}, steps=${steps})"

        and:
        def exitMsg = filterMethodLogs(appender, "weighted").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectMultiReturnService.weighted(value=${expected})"

        cleanup:
        detachAppender(appender)

        where:
        base | steps | expected
        1    | 0     | 0.0d    // no iterations
        1    | 1     | -1.0d   // odd only: acc -= 1
        1    | 2     | 1.0d    // odd then even: -1 + 2
        1    | 4     | 4.0d    // odd, even, odd, even: -1+2-1+4
    }

    // ── InspectMultiReturnService.average: FSTORE/FLOAD on local float, IINC on local int ──
    // Int params keep firstLocal=3; shiftAmount=2 shifts local float acc to slot 5 and int i to slot 6.

    def "average returns correct float from even and odd branches after instrumentation, confirming FSTORE/FLOAD LVT slots are shifted correctly"() {
        given:
        def bytes = transformWithRegistry(InspectMultiReturnService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMultiReturnService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."average"(total, count)

        then:
        result == expected

        and:
        def enterMsg = filterMethodLogs(appender, "average").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMultiReturnService.average(total=${total}, count=${count})"

        and:
        def exitMsg = filterMethodLogs(appender, "average").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectMultiReturnService.average(value=${expected})"

        cleanup:
        detachAppender(appender)

        where:
        total | count | expected
        10    | 0     | 10.0f   // no iterations
        10    | 1     | 10.5f   // even only (i=0): acc += 0.5f
        10    | 2     | 9.0f    // even then odd: +0.5f -1.5f
        10    | 4     | 8.0f    // even, odd, even, odd: +0.5f -1.5f +0.5f -1.5f
    }

    // ── InspectMultiReturnService.join: ASTORE/ALOAD on local StringBuilder, IINC on local int ──
    // firstLocal=3; shiftAmount=2 shifts local StringBuilder sb to slot 5 and int i to slot 6.

    def "join builds correct string from local StringBuilder after instrumentation, confirming ASTORE/ALOAD LVT slots are shifted correctly"() {
        given:
        def bytes = transformWithRegistry(InspectMultiReturnService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMultiReturnService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."join"(sep, count)

        then:
        result == expected

        and:
        def enterMsg = filterMethodLogs(appender, "join").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMultiReturnService.join(sep=${sep}, count=${count})"

        and:
        def exitMsg = filterMethodLogs(appender, "join").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectMultiReturnService.join(value=${expected})"

        cleanup:
        detachAppender(appender)

        where:
        sep  | count | expected
        "-"  | 0     | ""         // empty -- no ALOAD on sb body, only toString
        "-"  | 1     | "0"        // single element -- no separator append
        "-"  | 3     | "0-1-2"    // normal join with separator
        "|"  | 4     | "0|1|2|3"  // different separator, four elements
    }

    // ── InspectMultiReturnService.complex: all LVT-shifted instruction categories in one body ──
    // long+double params push firstLocal to 6; shiftAmount=2 shifts all 7 local variable slots.

    def "complex logs exact enter and exit and returns correct result, confirming all LVT shifts -- IINC, LSTORE, DSTORE, FSTORE, ASTORE -- are applied correctly when interleaved"() {
        given:
        def bytes = transformWithRegistry(InspectMultiReturnService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMultiReturnService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        // no break: all three items processed -- count=3, total=4, dsum=6.0, fsum=3.0f, buf=acd.
        when:
        def result = instance."complex"(10L, 2.0d, ["ab", "c", "d"])

        then:
        result == "3:4:6:3:acd"

        and:
        def enterMsg = filterMethodLogs(appender, "complex").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMultiReturnService.complex(limit=10, scale=2.0, items=[ab, c, d])"

        and:
        def exitMsg = filterMethodLogs(appender, "complex").find { it.contains("[EXIT]") }
        exitMsg == "|< [EXIT] InspectMultiReturnService.complex(value=3:4:6:3:acd)"

        cleanup:
        detachAppender(appender)
    }

    def "complex returns correct result from each branch combination after instrumentation -- no-break, early-break, skip-nulls, immediate-break"() {
        given:
        def bytes = transformWithRegistry(InspectMultiReturnService, InspectRegistry)
        def loaded = loadFresh(bytes, InspectMultiReturnService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        expect:
        instance."complex"(limit, scale, items) == expected

        where:
        limit | scale | items                      | expected
        10L   | 2.0d  | ["ab", "c", "d"]           | "3:4:6:3:acd"       // no break: all items consumed
        3L    | 2.0d  | ["ab", "cd", "ef"]         | "102:4:4:2:ac"      // break at i=1: count+=100
        100L  | 3.0d  | ["x", null, "y", ""]       | "0:2:6:2:xy"        // null and empty decrement count
        1L    | 1.0d  | ["hello"]                   | "101:5:1:1:h"       // immediate break on first item
    }

    // ── InspectOverloadService: 12 overloads of compute ──

    def "transformed InspectOverloadService has exactly one unique enter and exit synthetic method per compute overload with no duplicates"() {
        given:
        def bytes = transformWithRegistry(InspectOverloadService, InspectRegistry)

        when:
        def allMethods = collectDeclaredMethods(bytes)
        def enterMethods = allMethods.findAll { it.name.startsWith('$lp$enter$compute') }
        def exitMethods = allMethods.findAll { it.name.startsWith('$lp$exit$compute') }
        def cls = loadFresh(bytes, InspectOverloadService.name)
        cls.getDeclaredConstructor().newInstance()

        then:
        enterMethods.size() == 12
        enterMethods.collect { it.name }.toSet().size() == 12

        and:
        exitMethods.size() == 12
        exitMethods.collect { it.name }.toSet().size() == 12

        and:
        noExceptionThrown()
    }

    def "all 12 compute overloads return correct values and produce exact non-duplicate enter and exit logs after instrumentation"() {
        given:
        def bytes = transformWithRegistry(InspectOverloadService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectOverloadService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def r0  = instance."compute"()
        def r1  = instance."compute"(5)
        def r2  = instance."compute"(3L)
        def r3  = instance."compute"(2.5d)
        def r4  = instance."compute"("hello")
        def r5  = instance."compute"(3, 4)
        def r6  = instance."compute"(2, "abc")
        def r7  = instance."compute"("hello", 3)
        def r8  = instance."compute"("foo", "bar")
        def r9  = instance."compute"(1, 10L)
        def r10 = instance."compute"(["a", "b", "c"])
        def r11 = instance."compute"(1, 2, 3)

        then:
        r0  == "empty"
        r1  == "int:5"
        r2  == "long:3"
        r3  == "double:2"
        r4  == "str:5"
        r5  == "ii:7"
        r6  == "is:5"
        r7  == "si:hel"
        r8  == "ss:foobar"
        r9  == "il:11"
        r10 == "list:3"
        r11 == "iii:6"

        and:
        def logs = filterMethodLogs(appender, "compute")
        logs.size() == 24
        logs.toSet().size() == 24

        and:
        logs.containsAll([
            "|> [ENTER] InspectOverloadService.compute()",
            "|> [ENTER] InspectOverloadService.compute(a=5)",
            "|> [ENTER] InspectOverloadService.compute(a=3)",
            "|> [ENTER] InspectOverloadService.compute(a=2.5)",
            "|> [ENTER] InspectOverloadService.compute(s=hello)",
            "|> [ENTER] InspectOverloadService.compute(a=3, b=4)",
            "|> [ENTER] InspectOverloadService.compute(a=2, s=abc)",
            "|> [ENTER] InspectOverloadService.compute(s=hello, n=3)",
            "|> [ENTER] InspectOverloadService.compute(a=foo, b=bar)",
            "|> [ENTER] InspectOverloadService.compute(a=1, b=10)",
            "|> [ENTER] InspectOverloadService.compute(items=[a, b, c])",
            "|> [ENTER] InspectOverloadService.compute(a=1, b=2, c=3)"
        ])

        and:
        logs.containsAll([
            "|< [EXIT] InspectOverloadService.compute(value=empty)",
            "|< [EXIT] InspectOverloadService.compute(value=int:5)",
            "|< [EXIT] InspectOverloadService.compute(value=long:3)",
            "|< [EXIT] InspectOverloadService.compute(value=double:2)",
            "|< [EXIT] InspectOverloadService.compute(value=str:5)",
            "|< [EXIT] InspectOverloadService.compute(value=ii:7)",
            "|< [EXIT] InspectOverloadService.compute(value=is:5)",
            "|< [EXIT] InspectOverloadService.compute(value=si:hel)",
            "|< [EXIT] InspectOverloadService.compute(value=ss:foobar)",
            "|< [EXIT] InspectOverloadService.compute(value=il:11)",
            "|< [EXIT] InspectOverloadService.compute(value=list:3)",
            "|< [EXIT] InspectOverloadService.compute(value=iii:6)"
        ])

        cleanup:
        detachAppender(appender)
    }

    // ── InspectFilterService: reverse assertions for isUnloggableInfrastructureMethod and shouldIgnoreBusinessMethod ──

    def "transformed InspectFilterService emits synthetic methods only for publicAction -- constructor, non-public, static, Object overrides, and @AutomatedProcessingIgnore methods are all filtered"() {
        given:
        def bytes = transformWithRegistry(InspectFilterService, InspectRegistry)
        def allMethods = collectDeclaredMethods(bytes)
        def syntheticMethods = allMethods.findAll { it.name.startsWith('$lp$enter$') || it.name.startsWith('$lp$exit$') }

        expect:
        syntheticMethods.size() == 2
        syntheticMethods.count { it.name == '$lp$enter$publicAction' } == 1
        syntheticMethods.count { it.name == '$lp$exit$publicAction' } == 1

        and:
        syntheticMethods.every { !it.name.contains('<init>') }

        and:
        syntheticMethods.every { !it.name.contains('privateAction') }
        syntheticMethods.every { !it.name.contains('protectedAction') }
        syntheticMethods.every { !it.name.contains('packageAction') }

        and:
        syntheticMethods.every { !it.name.contains('staticAction') }

        and:
        syntheticMethods.every { !it.name.contains('$toString') }
        syntheticMethods.every { !it.name.contains('$equals') }
        syntheticMethods.every { !it.name.contains('$hashCode') }

        and:
        syntheticMethods.every { !it.name.contains('ignoredAction') }
    }

    def "InspectFilterService at runtime emits log events only for publicAction; all filtered methods produce zero log output"() {
        given:
        def bytes = transformWithRegistry(InspectFilterService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectFilterService.name)

        when:
        def instance = loaded.getDeclaredConstructor().newInstance()
        instance."privateAction"("p")
        instance."protectedAction"("q")
        instance."packageAction"("r")
        loaded."staticAction"("s")
        instance."ignoredAction"("i")
        instance.toString()
        instance.hashCode()
        instance.equals(instance)
        def result = instance."publicAction"("hello")

        then:
        result == "public:hello"

        and:
        appender.list.size() == 2

        and:
        def logs = appender.list*.formattedMessage
        logs[0] == "|> [ENTER] InspectFilterService.publicAction(input=hello)"
        logs[1] == "|< [EXIT] InspectFilterService.publicAction(value=public:hello)"

        cleanup:
        detachAppender(appender)
    }

    // ── InspectIgnoredClassService: class-level @AutomatedProcessingIgnore filters every method ──

    def "transformed InspectIgnoredClassService emits zero synthetic methods because the entire class is @AutomatedProcessingIgnore"() {
        given:
        def bytes = transformWithRegistry(InspectIgnoredClassService, InspectRegistry)
        def allMethods = collectDeclaredMethods(bytes)
        def syntheticMethods = allMethods.findAll { it.name.startsWith('$lp$enter$') || it.name.startsWith('$lp$exit$') }

        expect:
        syntheticMethods.isEmpty()
    }

    def "InspectIgnoredClassService at runtime produces zero log output for any method because the class is @AutomatedProcessingIgnore"() {
        given:
        def bytes = transformWithRegistry(InspectIgnoredClassService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectIgnoredClassService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def r1 = instance."process"("x")
        def r2 = instance."another"(5)

        then:
        r1 == "ignored-class:x"
        r2 == "ignored-class-another:5"

        and:
        appender.list.isEmpty()

        cleanup:
        detachAppender(appender)
    }

    // ── Bridge method filtering: InspectGenericService.process(String) generates a process(Object) bridge that must be skipped ──

    def "transformed InspectGenericService has exactly one synthetic enter and exit pair -- the compiler-generated bridge process(Object) is filtered out"() {
        given:
        def bytes = transformWithRegistry(InspectGenericService, InspectRegistry)
        def allMethods = collectDeclaredMethods(bytes)

        expect:
        def bridgeMethods = allMethods.findAll {
            it.name == "process" && (it.access & Opcodes.ACC_BRIDGE) != 0
        }
        bridgeMethods.size() == 1

        and:
        def enterMethods = allMethods.findAll { it.name.startsWith('$lp$enter$process') }
        def exitMethods = allMethods.findAll { it.name.startsWith('$lp$exit$process') }
        enterMethods.size() == 1
        exitMethods.size() == 1
    }

    // ── method enter/exit truncation marker contract ──

    def "synthetic enter log ends with ...[TRUNCATED]) when a parameter overflows the budget"() {
        given:
        def bytes = transformWithRegistry(InspectMaskedService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMaskedService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        LogRuntime.@boundMaxMessageLength = 60

        when:
        instance."lookup"(null, "x" * 200)

        then:
        def enterMsg = filterMethodLogs(appender, "lookup").find { it.contains("[ENTER]") }
        enterMsg.endsWith("...[TRUNCATED])")

        cleanup:
        detachAppender(appender)
        LogRuntime.@boundMaxMessageLength = 512
    }

    def "synthetic exit log ends with ...[TRUNCATED]) when the return value overflows the budget"() {
        given:
        def bytes = transformWithRegistry(InspectMaskedService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMaskedService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        LogRuntime.@boundMaxMessageLength = 60

        when:
        instance."lookup"(null, "y" * 200)

        then:
        def exitMsg = filterMethodLogs(appender, "lookup").find { it.contains("[EXIT]") }
        exitMsg.endsWith("...[TRUNCATED])")

        cleanup:
        detachAppender(appender)
        LogRuntime.@boundMaxMessageLength = 512
    }

    def "synthetic enter log does NOT include the render-truncation marker when output fits within the budget"() {
        given:
        def bytes = transformWithRegistry(InspectMaskedService, InspectRegistry)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InspectMaskedService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."lookup"(null, "short-value")

        then:
        def enterMsg = filterMethodLogs(appender, "lookup").find { it.contains("[ENTER]") }
        enterMsg == "|> [ENTER] InspectMaskedService.lookup(fallback=short-value)"
        !enterMsg.contains("...[TRUNCATED]")

        cleanup:
        detachAppender(appender)
    }

    // ── registries ──

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log.fixture.svc"],
            includeClassSuffixes = ["Svc"])
    static class SvcRegistry {}

    // ── helpers ──

    private static byte[] transformWithRegistry(Class<?> target, Class<?> registry) {
        def locator = ClassFileLocator.ForClassLoader.of(target.classLoader)
        def typePool = TypePool.Default.of(locator)
        def typeDesc = typePool.describe(target.name).resolve()
        def plugin = new AotLogByteBuddyPlugin(registry.name, locator, new AotCompileContext())
        def builder = new ByteBuddy().redefine(typeDesc, locator)
        plugin.apply(builder, typeDesc, locator).make().bytes
    }

    public static Logger TEST_INJECTED_LOGGER = null

    private static List loadFreshWithLogCapture(byte[] bytes, String className) {
        def loggerContext = new LoggerContext()
        loggerContext.start()
        def logger = loggerContext.getLogger(className)
        logger.level = Level.TRACE
        def appender = new ListAppender()
        appender.context = loggerContext
        appender.start()
        logger.addAppender(appender)
        TEST_INJECTED_LOGGER = logger

        def cr = new ClassReader(bytes)
        def cw = new ClassWriter(cr, 0)
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                def mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    void visitLdcInsn(Object value) {
                        if (value instanceof ConstantDynamic && value.getName() == "AOT_LOGGER") {
                            mv.visitFieldInsn(Opcodes.GETSTATIC,
                                    "org/libprunus/core/plugin/aot/log/InspectBehaviorIntegrationSpec",
                                    "TEST_INJECTED_LOGGER",
                                    "Lorg/slf4j/Logger;")
                        } else {
                            super.visitLdcInsn(value)
                        }
                    }
                }
            }
        }, 0)
        [loadFresh(cw.toByteArray(), className), appender]
    }

    private static Class<?> loadFresh(byte[] bytes, String className) {
        def loader = new ClassLoader(InspectBehaviorIntegrationSpec.classLoader) {
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name == className) return defineClass(name, bytes, 0, bytes.length)
                throw new ClassNotFoundException(name)
            }
            Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name == className) {
                    def c = findLoadedClass(name)
                    if (c == null) c = findClass(name)
                    if (resolve) resolveClass(c)
                    return c
                }
                return super.loadClass(name, resolve)
            }
        }
        loader.loadClass(className)
    }

    private static void detachAppender(ListAppender appender) {
        appender.stop()
        TEST_INJECTED_LOGGER = null
    }

    private static List<String> filterMethodLogs(ListAppender appender, String methodName) {
        appender.list*.formattedMessage.findAll { it.contains("." + methodName + "(") }
    }

    private static List<Map> collectDeclaredMethods(byte[] bytes) {
        def methods = []
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                methods << [name: name, descriptor: descriptor, access: access]
                return null
            }
        }, 0)
        methods
    }
}
