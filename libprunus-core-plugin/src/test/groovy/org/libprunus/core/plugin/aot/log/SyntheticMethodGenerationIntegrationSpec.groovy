package org.libprunus.core.plugin.aot.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.read.ListAppender
import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.ClassWriter
import net.bytebuddy.jar.asm.ConstantDynamic
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.pool.TypePool
import org.libprunus.core.config.CoreRuntimeConfig
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.log.annotation.MethodLoggingField
import org.libprunus.core.log.annotation.MethodLoggingProfile
import org.libprunus.core.log.runtime.AbstractLogConfig
import org.libprunus.core.log.runtime.LogLevel
import org.libprunus.core.log.runtime.LogRuntime
import org.libprunus.core.log.runtime.LogRuntimeConfig
import org.libprunus.core.plugin.aot.AotCompileContext
import org.libprunus.core.plugin.aot.log.WeavingInternalNames
import org.libprunus.core.plugin.aot.log.fixture.methodplan.LogOutputAllDiamondImpl
import org.libprunus.core.plugin.aot.log.fixture.methodplan.LogOutputIgnoreAllDiamondImpl
import org.slf4j.Logger
import spock.lang.Specification

class SyntheticMethodGenerationIntegrationSpec extends Specification {

    def setupSpec() {
        try {
            LogRuntime.initializeBinding(new AbstractLogConfig() {
                @Override int getMaxMessageLength() { return 512 }
                @Override boolean isWhitelisted(Class<?> type) { return false }
            })
        } catch (IllegalStateException ignored) {
        }
        LogRuntime.linkToDataPlane(new java.util.concurrent.atomic.AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(true))))
    }

    def setup() {
        LogRuntime.@boundMaxMessageLength = 512
        LogRuntime.linkToDataPlane(new java.util.concurrent.atomic.AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(true))))
    }

    // ── bytecode structure: synthetic method existence ──
    def "transformed class contains synthetic enter and exit methods for each public method"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def methods = collectDeclaredMethods(bytes)
        def enterMethods = methods.findAll { it.name.startsWith(WeavingInternalNames.SYNTHETIC_ENTER_PREFIX) }
        def exitMethods = methods.findAll { it.name.startsWith(WeavingInternalNames.SYNTHETIC_EXIT_PREFIX) }

        then:
        enterMethods.any { it.name == "${WeavingInternalNames.SYNTHETIC_ENTER_PREFIX}processOrder" }
        exitMethods.any { it.name == "${WeavingInternalNames.SYNTHETIC_EXIT_PREFIX}processOrder" }
    }

    def "synthetic enter method has ACC_PRIVATE ACC_STATIC ACC_SYNTHETIC access flags"() {
        given:
        def bytes = transformClass(TargetService, [])
        def methods = collectDeclaredMethods(bytes)

        when:
        def enterMethod = methods.find { it.name == "${WeavingInternalNames.SYNTHETIC_ENTER_PREFIX}processOrder" }

        then:
        (enterMethod.access & Opcodes.ACC_PRIVATE) != 0
        (enterMethod.access & Opcodes.ACC_STATIC) != 0
        (enterMethod.access & Opcodes.ACC_SYNTHETIC) != 0
    }

    def "synthetic exit method has ACC_PRIVATE ACC_STATIC ACC_SYNTHETIC access flags"() {
        given:
        def bytes = transformClass(TargetService, [])
        def methods = collectDeclaredMethods(bytes)

        when:
        def exitMethod = methods.find { it.name == "${WeavingInternalNames.SYNTHETIC_EXIT_PREFIX}processOrder" }

        then:
        (exitMethod.access & Opcodes.ACC_PRIVATE) != 0
        (exitMethod.access & Opcodes.ACC_STATIC) != 0
        (exitMethod.access & Opcodes.ACC_SYNTHETIC) != 0
    }

    def "no synthetic enrich method is generated when profile has no field extractors"() {
        given:
        def bytes = transformClass(TargetService, [])
        def methods = collectDeclaredMethods(bytes)

        expect:
        !methods.any { it.name == WeavingInternalNames.SYNTHETIC_ENRICH_METHOD }
    }

    // ── bytecode structure: enrich method with field extractors ──

    def "synthetic enrich method is generated when profile has field extractors"() {
        given:
        def extractors = [stringFieldExtractor()]
        def bytes = transformClass(TargetService, extractors)
        def methods = collectDeclaredMethods(bytes)

        when:
        def enrichMethod = methods.find { it.name == WeavingInternalNames.SYNTHETIC_ENRICH_METHOD }

        then:
        (enrichMethod.access & Opcodes.ACC_PRIVATE) != 0
        (enrichMethod.access & Opcodes.ACC_STATIC) != 0
        (enrichMethod.access & Opcodes.ACC_SYNTHETIC) != 0
        enrichMethod.descriptor == AsmDescriptors.ENRICH_METHOD_DESCRIPTOR
    }

    def "enrich method contains INVOKEINTERFACE addKeyValue call for each field extractor"() {
        given:
        def extractors = [stringFieldExtractor(), intFieldExtractor()]
        def bytes = transformClass(TargetService, extractors)

        when:
        def interfaceCalls = collectInterfaceCallsInMethod(bytes, WeavingInternalNames.SYNTHETIC_ENRICH_METHOD)

        then:
        interfaceCalls.count { it.name == "addKeyValue" && it.descriptor == AsmDescriptors.ADD_KEY_VALUE_DESCRIPTOR } == 2
    }

    def "enrich method invokes each field extractor via INVOKESTATIC"() {
        given:
        def extractors = [stringFieldExtractor(), intFieldExtractor()]
        def bytes = transformClass(TargetService, extractors)

        when:
        def staticCalls = collectStaticCallsInMethod(bytes, WeavingInternalNames.SYNTHETIC_ENRICH_METHOD)

        then:
        staticCalls.any { it.name == "getUserId" }
        staticCalls.any { it.name == "getCount" }
    }

    // ── field enrichment: autoboxing ──

    def "String-returning field extractor produces no boxing instruction in enrich method"() {
        given:
        def extractors = [stringFieldExtractor()]
        def bytes = transformClass(TargetService, extractors)

        when:
        def staticCalls = collectStaticCallsInMethod(bytes, WeavingInternalNames.SYNTHETIC_ENRICH_METHOD)

        then:
        !staticCalls.any { it.name == "valueOf" }
    }

    def "int-returning field extractor produces Integer.valueOf boxing instruction in enrich method"() {
        given:
        def extractors = [intFieldExtractor()]
        def bytes = transformClass(TargetService, extractors)

        when:
        def staticCalls = collectStaticCallsInMethod(bytes, WeavingInternalNames.SYNTHETIC_ENRICH_METHOD)

        then:
        staticCalls.any { it.owner == "java/lang/Integer" && it.name == "valueOf" }
    }

    // ── bytecode structure: logger constant ──

    def "AOT_LOGGER ConstantDynamic carries owner class name as bootstrap argument"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def constants = collectLdcInMethod(bytes, "processOrder")
        def loggerCondy = constants.find { it instanceof ConstantDynamic && it.getName() == "AOT_LOGGER" }

        then:
        loggerCondy.bootstrapMethodArgumentCount == 1
        loggerCondy.getBootstrapMethodArgument(0) == TargetService.name
    }

    // ── bytecode structure: enter method calls ──

    def "enter synthetic method calls StringBuilderPool.acquireWithPrefix"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "processOrder"
        def staticCalls = collectStaticCallsInMethod(bytes, enterMethodName)

        then:
        staticCalls.any {
            it.owner == WeavingInternalNames.STRING_BUILDER_POOL_INTERNAL_NAME && it.name == "acquireWithPrefix"
        }
    }

    def "enter synthetic method with multiple reference params avoids StringBuilder length and global config fetch"() {
        given:
        def bytes = transformClass(OverloadedService, [])
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "snapshot\$java_lang_String\$java_lang_String\$java_util_Map"

        when:
        def virtualCalls = collectVirtualCallsInMethod(bytes, enterMethodName)
        def lengthCalls = virtualCalls.findAll {
            it.owner == "java/lang/StringBuilder" && it.name == "length"
        }
        def renderCalls = virtualCalls.findAll {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME && it.name == "render"
        }
        def staticCalls = collectStaticCallsInMethod(bytes, enterMethodName)
        def maxLenCalls = staticCalls.findAll {
            it.owner == WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME && it.name == "getGlobalMaxMessageLength"
        }

        then:
        renderCalls.size() == 2
        lengthCalls.isEmpty()
        maxLenCalls.isEmpty()
    }

    def "enter synthetic method routes primitive parameter append through final primitive descriptor"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "processOrder"
        def virtualCalls = collectVirtualCallsInMethod(bytes, enterMethodName)

        then:
        virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME &&
                    it.name == "append" &&
                it.descriptor == AsmDescriptors.contextAppendPrimitiveDescriptor(Type.INT_TYPE)
        }
    }

    def "exit synthetic method routes primitive return append through final primitive descriptor"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "statusCode"
        def virtualCalls = collectVirtualCallsInMethod(bytes, exitMethodName)

        then:
        virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME &&
                    it.name == "append" &&
                it.descriptor == AsmDescriptors.contextAppendPrimitiveDescriptor(Type.INT_TYPE)
        }
    }

    def "exit synthetic method renders masked return value as static value=*** constant without reading return slot"() {
        given:
        def bytes = transformClass(MaskedReturnService, [])
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "fetchSecret"

        when:
        def ldcConstants = collectLdcInMethod(bytes, exitMethodName)
        def virtualCalls = collectVirtualCallsInMethod(bytes, exitMethodName)

        then:
        ldcConstants.any { it instanceof String && it.contains("value=" + WeavingInternalNames.MASK_SENTINEL + ")") }
        !virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME && it.name == "render"
        }
    }

    def "enter synthetic method routes scaffold string append through context append rather than StringBuilder.append(String)"() {
        given:
        def bytes = transformClass(TargetService, [])
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "processOrder"

        when:
        def enterVirtualCalls = collectVirtualCallsInMethod(bytes, enterMethodName)

        then:
        enterVirtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME &&
                    it.name == "append" &&
                    it.descriptor == AsmDescriptors.CONTEXT_APPEND_TEXT_DESCRIPTOR
        }
        !enterVirtualCalls.any {
            it.owner == "java/lang/StringBuilder" &&
                    it.name == "append" &&
                it.descriptor == "(Ljava/lang/String;)Ljava/lang/StringBuilder;"
        }
    }

    def "exit synthetic method routes scaffold string append through context append rather than StringBuilder.append(String)"() {
        given:
        def bytes = transformClass(TargetService, [])
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "processOrder"

        when:
        def exitVirtualCalls = collectVirtualCallsInMethod(bytes, exitMethodName)

        then:
        exitVirtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME &&
                    it.name == "append" &&
                    it.descriptor == AsmDescriptors.CONTEXT_APPEND_TEXT_DESCRIPTOR
        }
        !exitVirtualCalls.any {
            it.owner == "java/lang/StringBuilder" &&
                    it.name == "append" &&
                it.descriptor == "(Ljava/lang/String;)Ljava/lang/StringBuilder;"
        }
    }

    def "enter log message omits second parameter scaffold and value when first param alone exhausts shared budget"() {
        given: "a first param longer than maxMessageLength (512) so the budget is consumed before the second param is rendered"
        def bytes = transformClass(OverloadedService, [])
        def (loaded, appender) = loadFreshWithLogCapture(bytes, OverloadedService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."snapshot"("x" * 600, "distinctive-second-value")

        then: "the enter message is present but both second-param scaffold and value are absent because the budget was exhausted"
        def messages = filterMethodLogs(appender, "snapshot")
        def enterMsg = messages.find { it.contains("[ENTER]") }
        !enterMsg.contains("arg1=")
        !enterMsg.contains("distinctive-second-value")

        cleanup:
        detachAppender(loaded.name, appender)
    }

    def "enter log message omits primitive second parameter when first parameter exhausts shared budget"() {
        given: "a first param longer than maxMessageLength (512) so the shared budget is exhausted before primitive rendering"
        def bytes = transformClass(PrimitiveBudgetService, [])
        def (loaded, appender) = loadFreshWithLogCapture(bytes, PrimitiveBudgetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."snapshotPrimitive"("x" * 600, 2147483647)

        then:
        def messages = filterMethodLogs(appender, "snapshotPrimitive")
        def enterMsg = messages.find { it.contains("[ENTER]") }
        !enterMsg.contains("arg1=")
        !enterMsg.contains("2147483647")

        cleanup:
        detachAppender(loaded.name, appender)
    }

    def "enter synthetic method calls logAndRelease on StringBuilderWithContext"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "processOrder"
        def virtualCalls = collectVirtualCallsInMethod(bytes, enterMethodName)

        then:
        virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME && it.name == "logAndRelease"
        }
    }

    def "enter synthetic method single Throwable handler excludes Exception entry and delegates to handleRenderFailure"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "processOrder"
        def staticCalls = collectStaticCallsInMethod(bytes, enterMethodName)
        def exceptionTable = collectExceptionTableEntries(bytes, enterMethodName)

        then:
        !exceptionTable.any { it.type == "java/lang/Exception" }
        exceptionTable.any { it.type == "java/lang/Throwable" }
        staticCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME && it.name == "handleRenderFailure"
        }
    }

    def "enter synthetic method installs Throwable-scoped catch entry so Error is routed to pool-releasing handler"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "processOrder"
        def exceptionTable = collectExceptionTableEntries(bytes, enterMethodName)

        then:
        exceptionTable.any { it.type == "java/lang/Throwable" }
    }

    def "enter synthetic method calls enrich when field extractors are present"() {
        given:
        def extractors = [stringFieldExtractor()]
        def bytes = transformClass(TargetService, extractors)

        when:
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "processOrder"
        def staticCalls = collectStaticCallsInMethod(bytes, enterMethodName)

        then:
        staticCalls.any { it.name == WeavingInternalNames.SYNTHETIC_ENRICH_METHOD }
    }

    def "enter synthetic method does not call enrich when field extractors are absent"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "processOrder"
        def staticCalls = collectStaticCallsInMethod(bytes, enterMethodName)

        then:
        !staticCalls.any { it.name == WeavingInternalNames.SYNTHETIC_ENRICH_METHOD }
    }

    // ── bytecode structure: exit method calls ──

    def "exit synthetic method calls StringBuilderPool.acquireWithPrefix"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "processOrder"
        def staticCalls = collectStaticCallsInMethod(bytes, exitMethodName)

        then:
        staticCalls.any {
            it.owner == WeavingInternalNames.STRING_BUILDER_POOL_INTERNAL_NAME && it.name == "acquireWithPrefix"
        }
    }

    def "exit synthetic method calls logAndRelease on StringBuilderWithContext"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "processOrder"
        def virtualCalls = collectVirtualCallsInMethod(bytes, exitMethodName)

        then:
        virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME && it.name == "logAndRelease"
        }
    }

    def "exit synthetic method single Throwable handler excludes Exception entry and delegates to handleRenderFailure"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "processOrder"
        def staticCalls = collectStaticCallsInMethod(bytes, exitMethodName)
        def exceptionTable = collectExceptionTableEntries(bytes, exitMethodName)

        then:
        !exceptionTable.any { it.type == "java/lang/Exception" }
        exceptionTable.any { it.type == "java/lang/Throwable" }
        staticCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME && it.name == "handleRenderFailure"
        }
    }

    def "exit synthetic method installs Throwable-scoped catch entry so Error is routed to pool-releasing handler"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "processOrder"
        def exceptionTable = collectExceptionTableEntries(bytes, exitMethodName)

        then:
        exceptionTable.any { it.type == "java/lang/Throwable" }
    }

    def "exit synthetic method calls enrich when field extractors are present"() {
        given:
        def extractors = [stringFieldExtractor()]
        def bytes = transformClass(TargetService, extractors)

        when:
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "processOrder"
        def staticCalls = collectStaticCallsInMethod(bytes, exitMethodName)

        then:
        staticCalls.any { it.name == WeavingInternalNames.SYNTHETIC_ENRICH_METHOD }
    }

    def "exit synthetic method does not call enrich when field extractors are absent"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "processOrder"
        def staticCalls = collectStaticCallsInMethod(bytes, exitMethodName)

        then:
        !staticCalls.any { it.name == WeavingInternalNames.SYNTHETIC_ENRICH_METHOD }
    }

    // ── abort signal containment ──

    def "enter synthetic method swallows render graph abort signal from collection parameter rendering and allows method to complete"() {
        given:
        def bytes = transformClass(TargetService, [])
        def loaded = loadFresh(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        def throwingCollection = new AbstractList<String>() {
            @Override String get(int index) { return "item" }
            @Override int size() { return 1 }
            @Override Iterator<String> iterator() {
                new Iterator<String>() {
                    boolean hasNext() { throw new StackOverflowError("deep") }
                    String next() { return null }
                }
            }
        }

        when:
        def result = instance."deliverTo"(throwingCollection)

        then:
        noExceptionThrown()
        result == "delivered"
    }

    def "exit synthetic method swallows render graph abort signal from return value rendering and caller receives result"() {
        given:
        def bytes = transformClass(TargetService, [])
        def loaded = loadFresh(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        def throwingCollection = new AbstractList<String>() {
            @Override String get(int index) { return "item" }
            @Override int size() { return 1 }
            @Override Iterator<String> iterator() {
                new Iterator<String>() {
                    boolean hasNext() { throw new StackOverflowError("deep") }
                    String next() { return null }
                }
            }
        }

        when:
        def result = instance."getCollectionWith"(throwingCollection)

        then:
        noExceptionThrown()
        result.is(throwingCollection)
    }

    // ── StackOverflowError at trampoline level: pool release via Throwable handler ──

    def "enter synthetic method catches StackOverflowError thrown directly from try body and caller method proceeds normally"() {
        given: "a transformed class whose enter synthetic method is rewritten to always throw SOE"
        def bytes = transformClass(TargetService, [])
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "processOrder"
        def rewrittenBytes = rewriteSyntheticToThrowSOE(bytes, enterMethodName)
        def loaded = loadFresh(rewrittenBytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."processOrder"(42, "item")

        then: "SOE is caught by the Throwable handler; pool object is released; caller receives the original method's return value"
        noExceptionThrown()
        result == "order-42-item"
    }

    def "exit synthetic method catches StackOverflowError thrown directly from try body and caller receives original return value"() {
        given: "a transformed class whose exit synthetic method is rewritten to always throw SOE"
        def bytes = transformClass(TargetService, [])
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "processOrder"
        def rewrittenBytes = rewriteSyntheticToThrowSOE(bytes, exitMethodName)
        def loaded = loadFresh(rewrittenBytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."processOrder"(42, "item")

        then: "SOE is caught by the Throwable handler; pool object is released; caller receives the original method's return value"
        noExceptionThrown()
        result == "order-42-item"
    }

    // ── original method: inline elimination ──

    def "original processOrder method contains no StringBuilderPool acquire call"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def staticCalls = collectStaticCallsInMethod(bytes, "processOrder")

        then:
        !staticCalls.any { it.owner == WeavingInternalNames.STRING_BUILDER_POOL_INTERNAL_NAME && it.name == "acquire" }
    }

    def "original processOrder method calls its synthetic enter method via INVOKESTATIC"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def staticCalls = collectStaticCallsInMethod(bytes, "processOrder")

        then:
        staticCalls.any { it.name == WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "processOrder" }
    }

    def "original processOrder method calls its synthetic exit method via INVOKESTATIC"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def staticCalls = collectStaticCallsInMethod(bytes, "processOrder")

        then:
        staticCalls.any { it.name == WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "processOrder" }
    }

    // ── original method: no exception tables ──

    def "original method has zero exception table entries from logging injection"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def exceptionEntries = collectExceptionTableEntries(bytes, "processOrder")

        then:
        exceptionEntries.isEmpty()
    }

    // ── void method: exit method behavior ──

    def "void method produces exit synthetic method with Logger-only parameter"() {
        given:
        def bytes = transformClass(TargetService, [])
        def methods = collectDeclaredMethods(bytes)

        when:
        def exitMethod = methods.find { it.name == "${WeavingInternalNames.SYNTHETIC_EXIT_PREFIX}doWork" }

        then:
        exitMethod.descriptor == "(Lorg/slf4j/Logger;)V"
    }

    // ── exit OFF: no exit synthetic generated ──

    def "no exit synthetic method is generated when exit log level is OFF"() {
        given:
        def bytes = transformClassWithLevels(TargetService, [], LogLevel.INFO, LogLevel.OFF)
        def methods = collectDeclaredMethods(bytes)

        expect:
        !methods.any { it.name.startsWith(WeavingInternalNames.SYNTHETIC_EXIT_PREFIX) }

        and: "enter methods are still generated"
        methods.any { it.name.startsWith(WeavingInternalNames.SYNTHETIC_ENTER_PREFIX) }
    }

    // ── enter OFF: no enter synthetic generated ──

    def "no enter synthetic method is generated when enter log level is OFF"() {
        given:
        def bytes = transformClassWithLevels(TargetService, [], LogLevel.OFF, LogLevel.INFO)
        def methods = collectDeclaredMethods(bytes)

        expect:
        !methods.any { it.name.startsWith(WeavingInternalNames.SYNTHETIC_ENTER_PREFIX) }

        and: "exit methods are still generated"
        methods.any { it.name.startsWith(WeavingInternalNames.SYNTHETIC_EXIT_PREFIX) }
    }

    // ── execution correctness with field extractors ──

    def "transformed class with field extractors produces valid bytecode that executes without VerifyError"() {
        given:
        def extractors = [stringFieldExtractor(), intFieldExtractor()]
        def bytes = transformClass(TargetService, extractors)
        def loaded = loadFresh(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."processOrder"(42, "widget")

        then:
        noExceptionThrown()
        result == "order-42-widget"
    }

    def "transformed class without field extractors produces valid bytecode that executes without VerifyError"() {
        given:
        def bytes = transformClass(TargetService, [])
        def loaded = loadFresh(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."processOrder"(7, "item")

        then:
        noExceptionThrown()
        result == "order-7-item"
    }

    def "void method executes correctly after transformation with field extractors"() {
        given:
        def extractors = [stringFieldExtractor()]
        def bytes = transformClass(TargetService, extractors)
        def loaded = loadFresh(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."doWork"("test")

        then:
        noExceptionThrown()
    }

    def "static method on class with field extractors executes after transformation without VerifyError"() {
        given:
        def extractors = [stringFieldExtractor()]
        def bytes = transformClass(TargetService, extractors)
        def loaded = loadFresh(bytes, TargetService.name)

        when:
        def result = loaded."staticCompute"(3, 5)

        then:
        noExceptionThrown()
        result == 15
    }

    // ── multiple field extractors: ordering in enrich ──

    def "enrich method preserves field extractor ordering as declared"() {
        given:
        def extractors = [intFieldExtractor(), stringFieldExtractor()]
        def bytes = transformClass(TargetService, extractors)

        when:
        def ldcConstants = collectLdcInMethod(bytes, WeavingInternalNames.SYNTHETIC_ENRICH_METHOD)
        def fieldNameLdcs = ldcConstants.findAll { it instanceof String && (it == "count" || it == "userId") }

        then:
        fieldNameLdcs.size() == 2
        fieldNameLdcs[0] == "count"
        fieldNameLdcs[1] == "userId"
    }

    // ── return type classification in exit method ──

    def "exit synthetic method for List return type dispatches to render"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "getItems"
        def virtualCalls = collectVirtualCallsInMethod(bytes, exitMethodName)

        then:
        virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME && it.name == "render"
        }
        !virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME && it.name == "appendCollection"
        }
    }

    def "exit synthetic method for Map return type dispatches to render"() {
        given:
        def bytes = transformClass(TargetService, [])

        when:
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "getMapping"
        def virtualCalls = collectVirtualCallsInMethod(bytes, exitMethodName)

        then:
        virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME && it.name == "render"
        }
        !virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME && it.name == "appendMap"
        }
    }

    def "List-returning method executes correctly after transformation"() {
        given:
        def bytes = transformClass(TargetService, [])
        def loaded = loadFresh(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."getItems"()

        then:
        noExceptionThrown()
        (result as java.util.List).size() == 2
    }

    // ── log output capture: enter and exit messages ──

    def "transformed method emits enter log message with parameter names and values"() {
        given:
        def bytes = transformClass(TargetService, [])
        def (loaded, appender) = loadFreshWithLogCapture(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."processOrder"(42, "widget")

        then:
        def messages = filterMethodLogs(appender, "processOrder")
        messages.any { it == "|> [ENTER] SyntheticMethodGenerationIntegrationSpec\$TargetService.processOrder(arg0=42, arg1=widget)" }

        cleanup:
        detachAppender(loaded.name, appender)
    }

    def "transformed method emits exit log message with return value"() {
        given:
        def bytes = transformClass(TargetService, [])
        def (loaded, appender) = loadFreshWithLogCapture(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."processOrder"(42, "widget")

        then:
        def messages = filterMethodLogs(appender, "processOrder")
        messages.any { it == "|< [EXIT] SyntheticMethodGenerationIntegrationSpec\$TargetService.processOrder(value=order-42-widget)" }

        cleanup:
        detachAppender(loaded.name, appender)
    }

    def "void method emits exit log message with empty parentheses"() {
        given:
        def bytes = transformClass(TargetService, [])
        def (loaded, appender) = loadFreshWithLogCapture(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."doWork"("test")

        then:
        def messages = filterMethodLogs(appender, "doWork")
        messages.any { it == "|> [ENTER] SyntheticMethodGenerationIntegrationSpec\$TargetService.doWork(arg0=test)" }
        messages.any { it == "|< [EXIT] SyntheticMethodGenerationIntegrationSpec\$TargetService.doWork()" }

        cleanup:
        detachAppender(loaded.name, appender)
    }

    def "static method is excluded from method logging instrumentation"() {
        given:
        def bytes = transformClass(TargetService, [])
        def methods = collectDeclaredMethods(bytes)

        expect:
        !methods.any { it.name == WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "staticCompute" }
        !methods.any { it.name == WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "staticCompute" }
    }

    def "List-returning method emits exit log message with collection content"() {
        given:
        def bytes = transformClass(TargetService, [])
        def (loaded, appender) = loadFreshWithLogCapture(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."getItems"()

        then:
        def messages = filterMethodLogs(appender, "getItems")
        messages.any { it == "|< [EXIT] SyntheticMethodGenerationIntegrationSpec\$TargetService.getItems(value=[a, b])" }

        cleanup:
        detachAppender(loaded.name, appender)
    }

    def "static getter is excluded from method logging instrumentation"() {
        given:
        def bytes = transformClass(TargetService, [])
        def methods = collectDeclaredMethods(bytes)

        expect:
        !methods.any { it.name == WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "getServiceName" }
        !methods.any { it.name == WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "getServiceName" }
    }

    def "field extractors attach key-value pairs to every log event"() {
        given:
        def extractors = [stringFieldExtractor(), intFieldExtractor()]
        def bytes = transformClass(TargetService, extractors)
        def (loaded, appender) = loadFreshWithLogCapture(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."processOrder"(1, "x")

        then:
        def events = appender.list.findAll { it.formattedMessage.contains("processOrder") }
        events.size() == 2
        events.every { event ->
            def kvps = event.keyValuePairs
            kvps.any { it.key == "userId" && it.value == "user-123" } &&
            kvps.any { it.key == "count" && it.value == 42 }
        }

        cleanup:
        detachAppender(loaded.name, appender)
    }

    def "all captured log events are emitted at the configured log level"() {
        given:
        def bytes = transformClass(TargetService, [])
        def (loaded, appender) = loadFreshWithLogCapture(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."processOrder"(1, "x")

        then:
        def events = appender.list.findAll { it.formattedMessage.contains("processOrder") }
        events.size() == 2
        events.every { it.level == Level.INFO }

        cleanup:
        detachAppender(loaded.name, appender)
    }

    // ── overloaded methods: synthetic name disambiguation ──

    def "overloaded methods with same return type produce distinct synthetic exit methods"() {
        given: "a class with two snapshot overloads both returning Map"
        def bytes = transformClass(OverloadedService, [])
        def methods = collectDeclaredMethods(bytes)

        when:
        def exitMethods = methods.findAll { it.name.startsWith(WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "snapshot") }

        then:
        exitMethods.size() == 2
        exitMethods*.name as Set == ["${WeavingInternalNames.SYNTHETIC_EXIT_PREFIX}snapshot\$java_lang_String\$java_util_Map",
                         "${WeavingInternalNames.SYNTHETIC_EXIT_PREFIX}snapshot\$java_lang_String\$java_lang_String\$java_util_Map"] as Set
    }

    def "overloaded methods produce distinct synthetic enter methods"() {
        given:
        def bytes = transformClass(OverloadedService, [])
        def methods = collectDeclaredMethods(bytes)

        when:
        def enterMethods = methods.findAll { it.name.startsWith(WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "snapshot") }

        then:
        enterMethods.size() == 2
        enterMethods*.name as Set == ["${WeavingInternalNames.SYNTHETIC_ENTER_PREFIX}snapshot\$java_lang_String\$java_util_Map",
                          "${WeavingInternalNames.SYNTHETIC_ENTER_PREFIX}snapshot\$java_lang_String\$java_lang_String\$java_util_Map"] as Set
    }

    def "overloaded methods execute correctly after transformation"() {
        given:
        def bytes = transformClass(OverloadedService, [])
        def loaded = loadFresh(bytes, OverloadedService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result1 = instance."snapshot"("repo")
        def result2 = instance."snapshot"("repo", "wf-1")

        then:
        result1 == [repo: "repo", workflow: ""]
        result2 == [repo: "repo", workflow: "wf-1"]
    }

    // ── inherited interface annotation on parameter ──

    def "synthetic enter method masks parameter inherited from interface @Sensitive when concrete class has no annotation"() {
        given: "AnnotatedAnchorPort declares @Sensitive on parameter; InheritedAnnotationService implements it with no annotation"
        def bytes = transformClass(InheritedAnnotationService, [])
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "process"

        when:
        def ldcConstants = collectLdcInMethod(bytes, enterMethodName)

        then: "the mask sentinel '***' appears in an LDC constant, proving the inherited @Sensitive was resolved"
        ldcConstants.any { it instanceof String && it.contains(WeavingInternalNames.MASK_SENTINEL) }
    }

    def "synthetic enter method does not call render for a parameter masked via inherited interface @Sensitive"() {
        given:
        def bytes = transformClass(InheritedAnnotationService, [])
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "process"

        when:
        def virtualCalls = collectVirtualCallsInMethod(bytes, enterMethodName)

        then:
        !virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME && it.name == "render"
        }
    }

    def "transformed InheritedAnnotationService emits masked parameter in enter log when interface @Sensitive is inherited"() {
        given:
        def bytes = transformClass(InheritedAnnotationService, [])
        def (loaded, appender) = loadFreshWithLogCapture(bytes, InheritedAnnotationService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance."process"("secret-value")

        then:
        def messages = filterMethodLogs(appender, "process")
        def enterMsg = messages.find { it.contains("[ENTER]") }
        enterMsg.contains("***")
        !enterMsg.contains("secret-value")

        cleanup:
        detachAppender(loaded.name, appender)
    }

    // ── catch handler exceptionSlot isolated from eventSlot ──

    def "catch handler in enter synthetic method stores Throwable in exceptionSlot distinct from eventSlot"() {
        given: "enter method for processOrder: paramSlotCount=2 → eventSlot=3, contextSlot=4, exceptionSlot=5"
        def bytes = transformClass(TargetService, [])
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "processOrder"
        int expectedExceptionSlot = 5

        when:
        def astoreVars = collectAstoreVarsInMethod(bytes, enterMethodName)

        then: "eventSlot (3) is stored twice — once as null pre-init before try and once for the LoggingEventBuilder inside try; exceptionSlot (5) is used in catch"
        astoreVars.contains(expectedExceptionSlot)
        astoreVars.count { it == 3 } == 2
    }

    def "catch handler in exit synthetic method stores Throwable in exceptionSlot distinct from eventSlot"() {
        given: "exit method for processOrder: returnSlotCount=1 (String) → eventSlot=2, contextSlot=3, exceptionSlot=4"
        def bytes = transformClass(TargetService, [])
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "processOrder"
        int expectedExceptionSlot = 4

        when:
        def astoreVars = collectAstoreVarsInMethod(bytes, exitMethodName)

        then: "eventSlot (2) is stored twice — once as null pre-init before try and once for the LoggingEventBuilder inside try"
        astoreVars.contains(expectedExceptionSlot)
        astoreVars.count { it == 2 } == 2
    }

    def "enter synthetic method allocates eventSlot above wide parameter slot when method takes a long parameter"() {
        given: "enter method for wide(long): paramSlotCount=2 (long occupies 2 slots) → eventSlot=3, contextSlot=4, exceptionSlot=5"
        def bytes = transformClass(WideParamService, [])
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "wide"
        int expectedEventSlot = 3
        int expectedExceptionSlot = 5

        when:
        def astoreVars = collectAstoreVarsInMethod(bytes, enterMethodName)

        then:
        astoreVars.contains(expectedExceptionSlot)
        astoreVars.count { it == expectedEventSlot } == 2
    }

    // ── enrich invocation inside try scope ──

    def "enter synthetic method visits enrich call after tryStart label so field extractor exception is within scope"() {
        given:
        def bytes = transformClass(TargetService, [stringFieldExtractor()])
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "processOrder"
        def events = []
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int a, String name, String d, String s, String[] ex) {
                if (name != enterMethodName) return null
                return new MethodVisitor(Opcodes.ASM9) {
                    net.bytebuddy.jar.asm.Label tryStartLabel = null
                    net.bytebuddy.jar.asm.Label tryEndLabel = null
                    void visitTryCatchBlock(net.bytebuddy.jar.asm.Label start, net.bytebuddy.jar.asm.Label end,
                            net.bytebuddy.jar.asm.Label h, String type) {
                        if ('java/lang/Throwable' == type) { tryStartLabel = start; tryEndLabel = end }
                    }
                    void visitLabel(net.bytebuddy.jar.asm.Label l) {
                        if (l == tryStartLabel) events << 'tryStart'
                        else if (l == tryEndLabel) events << 'tryEnd'
                    }
                    void visitMethodInsn(int op, String owner, String n, String desc, boolean iface) {
                        if (op == Opcodes.INVOKESTATIC && n == WeavingInternalNames.SYNTHETIC_ENRICH_METHOD) events << 'enrich'
                    }
                }
            }
        }, 0)

        when:
        def tryStartIdx = events.indexOf('tryStart')
        def enrichIdx = events.indexOf('enrich')

        then: "enrich appears after tryStart, so a RuntimeException from the extractor is caught by the exception table"
        tryStartIdx >= 0
        enrichIdx > tryStartIdx
    }

    def "exit synthetic method visits enrich call after tryStart label so field extractor exception is within scope"() {
        given:
        def bytes = transformClass(TargetService, [stringFieldExtractor()])
        def exitMethodName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "processOrder"
        def events = []
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int a, String name, String d, String s, String[] ex) {
                if (name != exitMethodName) return null
                return new MethodVisitor(Opcodes.ASM9) {
                    net.bytebuddy.jar.asm.Label tryStartLabel = null
                    net.bytebuddy.jar.asm.Label tryEndLabel = null
                    void visitTryCatchBlock(net.bytebuddy.jar.asm.Label start, net.bytebuddy.jar.asm.Label end,
                            net.bytebuddy.jar.asm.Label h, String type) {
                        if ('java/lang/Throwable' == type) { tryStartLabel = start; tryEndLabel = end }
                    }
                    void visitLabel(net.bytebuddy.jar.asm.Label l) {
                        if (l == tryStartLabel) events << 'tryStart'
                        else if (l == tryEndLabel) events << 'tryEnd'
                    }
                    void visitMethodInsn(int op, String owner, String n, String desc, boolean iface) {
                        if (op == Opcodes.INVOKESTATIC && n == WeavingInternalNames.SYNTHETIC_ENRICH_METHOD) events << 'enrich'
                    }
                }
            }
        }, 0)

        when:
        def tryStartIdx = events.indexOf('tryStart')
        def enrichIdx = events.indexOf('enrich')

        then: "enrich appears after tryStart, so a RuntimeException from the extractor is caught by the exception table"
        tryStartIdx >= 0
        enrichIdx > tryStartIdx
    }

    def "field extractor RuntimeException is swallowed by the try scope and does not propagate to the caller"() {
        given:
        def bytes = transformWithRegistry(TargetService, RegistryThrowingFieldInfoInfo)
        def loaded = loadFresh(bytes, TargetService.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance."processOrder"(42, "widget")

        then:
        noExceptionThrown()
        result == "order-42-widget"
    }

    // ── conflict detection: same-layer irreconcilable annotations ──

    def "plugin transformation throws when same-layer ancestors have conflicting IGNORE and ALL param annotations"() {
        given:
        def context = new AotCompileContext()

        when:
        transformWithContext(LogOutputIgnoreAllDiamondImpl, RegistryForConflictImpl, context)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("conflict")
        AotCompileContext.isMissingMask(context.peekMask(LogOutputIgnoreAllDiamondImpl.name))
    }

    def "plugin transformation throws when same-layer ancestors have conflicting @Sensitive and @DoLog annotations"() {
        given:
        def context = new AotCompileContext()

        when:
        transformWithContext(LogOutputAllDiamondImpl, RegistryForConflictImpl, context)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("conflict")
        AotCompileContext.isMissingMask(context.peekMask(LogOutputAllDiamondImpl.name))
    }

    private static List<Integer> collectAstoreVarsInMethod(byte[] bytes, String targetMethod) {
        def vars = []
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name != targetMethod) return null
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitVarInsn(int opcode, int varIndex) {
                        if (opcode == Opcodes.ASTORE) {
                            vars << varIndex
                        }
                    }
                }
            }
        }, 0)
        vars
    }

    @org.libprunus.core.log.annotation.Sensitive
    interface AnnotatedAnchorPort {
        String process(@org.libprunus.core.log.annotation.Sensitive String input)
    }

    static class InheritedAnnotationService implements AnnotatedAnchorPort {
        @Override
        String process(String input) {
            return input
        }
    }

    private static byte[] transformClass(Class<?> target, List<FieldExtractorRef> fieldExtractors) {
        transformClassWithLevels(target, fieldExtractors, LogLevel.INFO, LogLevel.INFO)
    }

    private static byte[] rewriteSyntheticToThrowSOE(byte[] bytes, String targetMethodName) {
        def cr = new ClassReader(bytes)
        def cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if (name != targetMethodName) return super.visitMethod(access, name, desc, sig, ex)
                def mv = super.visitMethod(access, name, desc, sig, ex)
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitCode() {
                        mv.visitCode()
                        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StackOverflowError")
                        mv.visitInsn(Opcodes.DUP)
                        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StackOverflowError", "<init>", "()V", false)
                        mv.visitInsn(Opcodes.ATHROW)
                        mv.visitMaxs(0, 0)
                        mv.visitEnd()
                    }
                }
            }
        }, 0)
        cw.toByteArray()
    }

    private static byte[] transformWithRegistry(Class<?> target, Class<?> registry) {
        transformWithContext(target, registry, new AotCompileContext())
    }

    private static byte[] transformWithContext(Class<?> target, Class<?> registry, AotCompileContext context) {
        def locator = ClassFileLocator.ForClassLoader.of(target.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(target.name).resolve()
        def plugin = new AotLogByteBuddyPlugin(registry.name, locator, context)
        def builder = new ByteBuddy().redefine(typeDesc, locator)
        plugin.apply(builder, typeDesc, locator).make().bytes
    }

    private static byte[] transformClassWithLevels(
            Class<?> target, List<FieldExtractorRef> fieldExtractors, LogLevel enter, LogLevel exit) {
        def registryClass = resolveRegistryClass(fieldExtractors, enter, exit)
        def locator = ClassFileLocator.ForClassLoader.of(target.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(target.name).resolve()
        def plugin = new AotLogByteBuddyPlugin(registryClass.name, locator, new AotCompileContext())
        def builder = new ByteBuddy().redefine(typeDesc, locator)
        plugin.apply(builder, typeDesc, locator).make().bytes
    }

    private static Class<?> resolveRegistryClass(
            List<FieldExtractorRef> fieldExtractors, LogLevel enter, LogLevel exit) {
        def keys = fieldExtractors.collect { it.fieldName() }
        if (enter == LogLevel.INFO && exit == LogLevel.INFO) {
            if (keys == []) {
                return RegistryNoFieldsInfoInfo
            }
            if (keys == ["userId"]) {
                return RegistryUserIdInfoInfo
            }
            if (keys == ["count"]) {
                return RegistryCountInfoInfo
            }
            if (keys == ["userId", "count"]) {
                return RegistryUserIdCountInfoInfo
            }
            if (keys == ["count", "userId"]) {
                return RegistryCountUserIdInfoInfo
            }
        }
        if (enter == LogLevel.INFO && exit == LogLevel.OFF && keys == []) {
            return RegistryNoFieldsInfoOff
        }
        if (enter == LogLevel.OFF && exit == LogLevel.INFO && keys == []) {
            return RegistryNoFieldsOffInfo
        }
        throw new IllegalArgumentException("Unsupported test profile combination")
    }

    private static Class<?> loadFresh(byte[] bytes, String className) {
        def loader = new ClassLoader(SyntheticMethodGenerationIntegrationSpec.classLoader) {
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name == className) {
                    return defineClass(name, bytes, 0, bytes.length)
                }
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

        // Rewrite the generated class Condy to directly fetch the test logger so production code needs no testing hooks
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
                            mv.visitFieldInsn(Opcodes.GETSTATIC, "org/libprunus/core/plugin/aot/log/SyntheticMethodGenerationIntegrationSpec", "TEST_INJECTED_LOGGER", "Lorg/slf4j/Logger;")
                        } else {
                            super.visitLdcInsn(value)
                        }
                    }
                }
            }
        }, 0)

        def loaded = loadFresh(cw.toByteArray(), className)
        [loaded, appender]
    }

    private static void detachAppender(String loggerName, ListAppender appender) {
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

    private static List<Map> collectStaticCallsInMethod(byte[] bytes, String targetMethod) {
        def calls = []
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name != targetMethod) return null
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitMethodInsn(int opcode, String owner, String n, String d, boolean iface) {
                        if (opcode == Opcodes.INVOKESTATIC) {
                            calls << [owner: owner, name: n, descriptor: d]
                        }
                    }
                }
            }
        }, 0)
        calls
    }

    private static List<Map> collectVirtualCallsInMethod(byte[] bytes, String targetMethod) {
        def calls = []
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name != targetMethod) return null
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitMethodInsn(int opcode, String owner, String n, String d, boolean iface) {
                        if (opcode == Opcodes.INVOKEVIRTUAL) {
                            calls << [owner: owner, name: n, descriptor: d]
                        }
                    }
                }
            }
        }, 0)
        calls
    }

    private static List<Map> collectInterfaceCallsInMethod(byte[] bytes, String targetMethod) {
        def calls = []
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name != targetMethod) return null
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitMethodInsn(int opcode, String owner, String n, String d, boolean iface) {
                        if (opcode == Opcodes.INVOKEINTERFACE) {
                            calls << [owner: owner, name: n, descriptor: d]
                        }
                    }
                }
            }
        }, 0)
        calls
    }

    private static List<Object> collectLdcInMethod(byte[] bytes, String targetMethod) {
        def constants = []
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name != targetMethod) return null
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitLdcInsn(Object value) {
                        constants << value
                    }
                }
            }
        }, 0)
        constants
    }

    private static List<Map> collectExceptionTableEntries(byte[] bytes, String targetMethod) {
        def entries = []
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name != targetMethod) return null
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitTryCatchBlock(
                            net.bytebuddy.jar.asm.Label start,
                            net.bytebuddy.jar.asm.Label end,
                            net.bytebuddy.jar.asm.Label handler,
                            String type) {
                        entries << [type: type]
                    }
                }
            }
        }, 0)
        entries
    }

    private static FieldExtractorRef stringFieldExtractor() {
        new FieldExtractorRef(
                "userId",
                FieldExtractorHolder.name.replace('.', '/'),
                "getUserId",
            "()Ljava/lang/String;",
            false)
    }

    private static FieldExtractorRef intFieldExtractor() {
        new FieldExtractorRef(
                "count",
                FieldExtractorHolder.name.replace('.', '/'),
                "getCount",
            "()I",
            false)
    }

    @LogRegistry
    @MethodLoggingProfile(includePackages = ["org.libprunus.core.plugin.aot.log"], includeClassSuffixes = ["Service"])
    static class RegistryNoFieldsInfoInfo {}

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log.fixture.methodplan"],
            includeClassSuffixes = ["Impl"])
    static class RegistryForConflictImpl {}

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Service"],
            fields = ["userId"])
    static class RegistryUserIdInfoInfo {
        @MethodLoggingField("userId")
        static String getUserId() {
            return "user-123"
        }
    }

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Service"],
            fields = ["count"])
    static class RegistryCountInfoInfo {
        @MethodLoggingField("count")
        static int getCount() {
            return 42
        }
    }

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Service"],
            fields = ["userId", "count"])
    static class RegistryUserIdCountInfoInfo {
        @MethodLoggingField("userId")
        static String getUserId() {
            return "user-123"
        }

        @MethodLoggingField("count")
        static int getCount() {
            return 42
        }
    }

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Service"],
            fields = ["count", "userId"])
    static class RegistryCountUserIdInfoInfo {
        @MethodLoggingField("userId")
        static String getUserId() {
            return "user-123"
        }

        @MethodLoggingField("count")
        static int getCount() {
            return 42
        }
    }

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Service"],
            entryLevel = LogLevel.INFO,
            exitLevel = LogLevel.OFF)
    static class RegistryNoFieldsInfoOff {}

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Service"],
            entryLevel = LogLevel.OFF,
            exitLevel = LogLevel.INFO)
    static class RegistryNoFieldsOffInfo {}

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Service"],
            fields = ["throwingField"])
    static class RegistryThrowingFieldInfoInfo {
        @org.libprunus.core.log.annotation.MethodLoggingField("throwingField")
        static String getThrowingField() {
            throw new RuntimeException("extractor-error")
        }
    }

    static class FieldExtractorHolder {
        public static String getUserId() { return "user-123" }
        public static int getCount() { return 42 }
    }

    static class TargetService {
        public String processOrder(int orderId, String item) {
            return "order-" + orderId + "-" + item
        }

        public int statusCode() {
            return 200
        }

        public void doWork(String label) {
            label.length()
        }

        public static int staticCompute(int a, int b) {
            return a * b
        }

        public java.util.List<String> getItems() {
            return java.util.List.of("a", "b")
        }

        public java.util.Map<String, Integer> getMapping() {
            return java.util.Map.of("x", 1)
        }

        public static String getServiceName() {
            return "test-service"
        }

        public String deliverTo(java.util.Collection<String> destinations) {
            return "delivered"
        }

        public java.util.Collection<String> getCollectionWith(java.util.Collection<String> items) {
            return items
        }
    }

    static class OverloadedService {
        public java.util.Map<String, Object> snapshot(String rawRepoPath) {
            return snapshot(rawRepoPath, "")
        }

        public java.util.Map<String, Object> snapshot(String rawRepoPath, String workflowId) {
            return java.util.Map.of("repo", rawRepoPath, "workflow", workflowId)
        }
    }

    static class PrimitiveBudgetService {
        public java.util.Map<String, Object> snapshotPrimitive(String rawRepoPath, int runId) {
            return java.util.Map.of("repo", rawRepoPath, "run", runId)
        }
    }

    static class MaskedReturnService {
        @org.libprunus.core.log.annotation.Sensitive
        public String fetchSecret(String key) {
            return "secret-" + key
        }
    }

    static class WideParamService {
        public void wide(long id) {
        }
    }
}
