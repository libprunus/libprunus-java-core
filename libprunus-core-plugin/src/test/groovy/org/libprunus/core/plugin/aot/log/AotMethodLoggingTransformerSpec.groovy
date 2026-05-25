package org.libprunus.core.plugin.aot.log

import java.util.Collections
import java.util.Optional
import net.bytebuddy.description.annotation.AnnotationList
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.method.MethodList
import net.bytebuddy.description.method.ParameterList
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.description.type.TypeList
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.ClassWriter
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import org.libprunus.core.log.runtime.LogLevel
import spock.lang.Specification

class AotMethodLoggingTransformerSpec extends Specification {

    private static final String BINARY_NAME = "sample.App"
    private static final String CLASS_NAME = "App"
    private static final int COMPUTE_MASK = ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS

    def "mergeWriter ORs incoming flags with COMPUTE_FRAMES and COMPUTE_MAXS and preserves non-mask bits"() {
        given:
        def transformer = new AotMethodLoggingTransformer(routeGraphEmpty())

        when:
        int result = transformer.mergeWriter(flags)

        then:
        (result & COMPUTE_MASK) == COMPUTE_MASK
        (result & ~COMPUTE_MASK) == (flags & ~COMPUTE_MASK)

        where:
        flags << [
            0,
            ClassWriter.COMPUTE_FRAMES,
            ClassWriter.COMPUTE_MAXS,
            ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS,
            ClassWriter.COMPUTE_FRAMES | 0xFF00,
        ]
    }

    def "sanitizeForRecipe returns the same reference when no control characters are present"() {
        given:
        def input = "com.example.MyService"

        when:
        def result = AotMethodLoggingTransformer.sanitizeForRecipe(input)

        then:
        result.is(input)
    }

    def "sanitizeForRecipe replaces ISO control characters with question marks and produces a new instance"() {
        when:
        def result = AotMethodLoggingTransformer.sanitizeForRecipe(text)

        then:
        result == expected
        !result.is(text)

        where:
        text                        || expected
        "ab"                  || "a?b"
        "ab"                  || "a?b"
        "abc"           || "a?b?c"
        "leading"             || "?leading"
        "trailing"            || "trailing?"
        "ab"                  || "a?b"
        "EndCtrl\n"                 || "EndCtrl?"
        "\t\r\n"                    || "???"
    }

    def "sanitizeForRecipe returns the same empty string reference for an empty input"() {
        given:
        def input = ""

        when:
        def result = AotMethodLoggingTransformer.sanitizeForRecipe(input)

        then:
        result.is(input)
    }

    def "inner visit captures the class internal name from the underlying ASM visit callback"() {
        given:
        def graph = realGraph([realMethodNode("doWork", "()V")])
        def transformer = new AotMethodLoggingTransformer(graph)
        def methodDescStub = nonBridgeMethodDescStub("doWork", "()V")
        def methods = Stub(MethodList) { iterator() >> { [methodDescStub].iterator() } }
        def cv = transformer.wrap(
                typeDescriptionStub(),
                recordingClassVisitor([]),
                null, null, null,
                methods,
                0, 0)

        when:
        cv.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)

        then:
        cv.classInternalName == "com/example/Foo"
    }

    def "visitMethod returns null for a pre-existing synthetic enter method"() {
        given:
        def graph = realGraph([])
        def transformer = new AotMethodLoggingTransformer(graph)
        def recordedNames = []
        def cv = transformer.wrap(
                typeDescriptionStub(),
                recordingClassVisitor(recordedNames),
                null, null, null,
                Stub(MethodList) { iterator() >> [].iterator() },
                0, 0)
        def syntheticName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "doWork\$void"

        when:
        def result = cv.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                syntheticName, "()V", null, null)

        then:
        result == null
        !recordedNames.contains(syntheticName)
    }

    def "visitMethod returns null for a pre-existing synthetic exit method"() {
        given:
        def graph = realGraph([])
        def transformer = new AotMethodLoggingTransformer(graph)
        def recordedNames = []
        def cv = transformer.wrap(
                typeDescriptionStub(),
                recordingClassVisitor(recordedNames),
                null, null, null,
                Stub(MethodList) { iterator() >> [].iterator() },
                0, 0)
        def syntheticName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "doWork\$void"

        when:
        def result = cv.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                syntheticName, "()V", null, null)

        then:
        result == null
        !recordedNames.contains(syntheticName)
    }

    def "visitMethod still produces a downstream MethodVisitor for ordinary method names alongside synthetic name short circuit"() {
        given:
        def graph = realGraph([])
        def transformer = new AotMethodLoggingTransformer(graph)
        def recordedNames = []
        def cv = transformer.wrap(
                typeDescriptionStub(),
                recordingClassVisitor(recordedNames),
                null, null, null,
                Stub(MethodList) { iterator() >> [].iterator() },
                0, 0)

        when:
        def result = cv.visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)

        then:
        result instanceof MethodVisitor
        recordedNames.contains("doWork")
    }

    def "visitMethod returns the underlying delegate when the method is not in the instrumented method list"() {
        given:
        def methodNode = realMethodNode("doWork", "()V")
        def graph = realGraph([methodNode])
        def transformer = new AotMethodLoggingTransformer(graph)
        def (sentinel, wrappedCv) = sentinelAndDelegate()
        def methods = Stub(MethodList) { iterator() >> { [].iterator() } }
        def cv = transformer.wrap(
                typeDescriptionStub(),
                wrappedCv,
                null, null, null,
                methods,
                0, 0)

        expect:
        cv.visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null).is(sentinel)
    }

    def "visitMethod returns the underlying delegate when the method node marks the shape ineligible for enter exit emission"() {
        given:
        def methodNode = realMethodNode("doWork", "()V", false)
        def graph = realGraph([methodNode])
        def transformer = new AotMethodLoggingTransformer(graph)
        def (sentinel, wrappedCv) = sentinelAndDelegate()
        def methodDescStub = nonBridgeMethodDescStub("doWork", "()V")
        def methods = Stub(MethodList) { iterator() >> { [methodDescStub].iterator() } }
        def cv = transformer.wrap(
                typeDescriptionStub(),
                wrappedCv,
                null, null, null,
                methods,
                0, 0)

        expect:
        cv.visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null).is(sentinel)
    }

    def "visitMethod wraps an eligible ordinary method with a LightweightInjectionVisitor"() {
        given:
        def methodNode = realMethodNode("doWork", "()V")
        def graph = realGraph([methodNode])
        def transformer = new AotMethodLoggingTransformer(graph)
        def (sentinel, wrappedCv) = sentinelAndDelegate()
        def methodDescStub = nonBridgeMethodDescStub("doWork", "()V")
        def methods = Stub(MethodList) { iterator() >> { [methodDescStub].iterator() } }
        def cv = transformer.wrap(
                typeDescriptionStub(),
                wrappedCv,
                null, null, null,
                methods,
                0, 0)

        when:
        def result = cv.visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)

        then:
        result instanceof LightweightInjectionVisitor
    }

    def "visitMethod routes className and methodName through sanitizeForRecipe when building MethodLogContext"() {
        given:
        def graph = realGraphWithDeclaringSimpleName(
                [realMethodNode("doWork", "()V")], "AppCtrl")
        def transformer = new AotMethodLoggingTransformer(graph)
        def methodDescStub = nonBridgeMethodDescStub("doWork", "()V")
        def methods = Stub(MethodList) { iterator() >> { [methodDescStub].iterator() } }
        def cv = transformer.wrap(
                typeDescriptionStub(),
                recordingClassVisitor([]),
                null, null, null,
                methods,
                0, 0)

        when:
        cv.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/App", null, "java/lang/Object", null)
        cv.visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)

        then:
        cv.syntheticRequests.size() == 1
        def context = cv.syntheticRequests[0].context()
        context.renderedClassName() == "App?Ctrl"
        context.renderedMethodName() == "do?Work"
        !context.renderedClassName().contains("")
        !context.renderedMethodName().contains("")
    }

    def "visitMethod emits synthetic name without overload suffix when the method has no eligible overload"() {
        given:
        def graph = realGraph([realMethodNode("doWork", "()V")])
        def transformer = new AotMethodLoggingTransformer(graph)
        def recordedNames = []
        def recordingCv = recordingClassVisitor(recordedNames)
        def methodDescStub = nonBridgeMethodDescStub("doWork", "()V")
        def methods = Stub(MethodList) { iterator() >> { [methodDescStub].iterator() } }
        def cv = transformer.wrap(
                typeDescriptionStub(),
                recordingCv,
                null, null, null,
                methods,
                0, 0)

        when:
        cv.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/App", null, "java/lang/Object", null)
        cv.visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        cv.visitEnd()

        then:
        recordedNames.contains(WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "doWork")
        recordedNames.contains(WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "doWork")
        !recordedNames.contains(WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "doWork\$void\$void")
        !recordedNames.contains(WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "doWork\$void\$void")
    }

    def "visitEnd emits enrich and synthetic enter and exit methods according to log levels and extractor configuration"() {
        given:
        def graph = realGraph([realMethodNode("doWork", "()V")], enterLevel, exitLevel, fieldExtractors)
        def transformer = new AotMethodLoggingTransformer(graph)
        def recordedNames = []
        def recordingCv = recordingClassVisitor(recordedNames)
        def methodDescStub = nonBridgeMethodDescStub("doWork", "()V")
        def methods = Stub(MethodList) { iterator() >> { [methodDescStub].iterator() } }
        def cv = transformer.wrap(
                typeDescriptionStub(),
                recordingCv,
                null, null, null,
                methods,
                0, 0)

        when:
        cv.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/App", null, "java/lang/Object", null)
        cv.visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        cv.visitEnd()

        then:
        recordedNames.contains(WeavingInternalNames.SYNTHETIC_ENRICH_METHOD) == enrichEmitted
        recordedNames.any { it.startsWith(WeavingInternalNames.SYNTHETIC_ENTER_PREFIX) } == enterEmitted
        recordedNames.any { it.startsWith(WeavingInternalNames.SYNTHETIC_EXIT_PREFIX) } == exitEmitted

        where:
        enterLevel     | exitLevel      | fieldExtractors                                                                                  || enrichEmitted | enterEmitted | exitEmitted
        LogLevel.DEBUG | LogLevel.DEBUG | [new FieldExtractorRef("traceId", "com/example/Foo", "getTrace", "()Ljava/lang/String;", false)] || true          | true         | true
        LogLevel.DEBUG | LogLevel.DEBUG | []                                                                                               || false         | true         | true
        LogLevel.OFF   | LogLevel.DEBUG | []                                                                                               || false         | false        | true
        LogLevel.DEBUG | LogLevel.OFF   | []                                                                                               || false         | true         | false
        LogLevel.OFF   | LogLevel.OFF   | []                                                                                               || false         | false        | false
    }

    def "visitEnd emits overload-suffixed synthetic methods when the method name has multiple eligible overloads"() {
        given:
        def methodNodeVoid = realMethodNode("doWork", "()V")
        def methodNodeInt = realMethodNode("doWork", "()I")
        def graph = realGraph([methodNodeVoid, methodNodeInt])
        def transformer = new AotMethodLoggingTransformer(graph)
        def recordedNames = []
        def recordingCv = recordingClassVisitor(recordedNames)
        def methodDescVoid = nonBridgeMethodDescStub("doWork", "()V", void.class)
        def methodDescInt = nonBridgeMethodDescStub("doWork", "()I", int.class)
        def methods = Stub(MethodList) { iterator() >> { [methodDescVoid, methodDescInt].iterator() } }
        def cv = transformer.wrap(
                typeDescriptionStub(),
                recordingCv,
                null, null, null,
                methods,
                0, 0)

        when:
        cv.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/App", null, "java/lang/Object", null)
        cv.visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        cv.visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()I", null, null)
        cv.visitEnd()

        then:
        recordedNames.contains(WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "doWork\$void\$void")
        recordedNames.contains(WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "doWork\$void\$int")
        recordedNames.contains(WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "doWork\$void\$void")
        recordedNames.contains(WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "doWork\$void\$int")
        !recordedNames.contains(WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "doWork")
        !recordedNames.contains(WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "doWork")
    }

    def "visitEnd emits enrich method even when no synthetic enter or exit requests were collected"() {
        given:
        def extractor = new FieldExtractorRef("traceId", "com/example/Foo", "getTrace", "()Ljava/lang/String;", false)
        def ineligibleMethodNode = realMethodNode("doWork", "()V", false)
        def graph = realGraph([ineligibleMethodNode], LogLevel.DEBUG, LogLevel.DEBUG, [extractor])
        def transformer = new AotMethodLoggingTransformer(graph)
        def recordedNames = []
        def recordingCv = recordingClassVisitor(recordedNames)
        def methodDescStub = nonBridgeMethodDescStub("doWork", "()V")
        def methods = Stub(MethodList) { iterator() >> { [methodDescStub].iterator() } }
        def cv = transformer.wrap(
                typeDescriptionStub(),
                recordingCv,
                null, null, null,
                methods,
                0, 0)

        when:
        cv.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/App", null, "java/lang/Object", null)
        cv.visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        cv.visitEnd()

        then:
        recordedNames.contains(WeavingInternalNames.SYNTHETIC_ENRICH_METHOD)
        !recordedNames.any { it.startsWith(WeavingInternalNames.SYNTHETIC_ENTER_PREFIX) }
        !recordedNames.any { it.startsWith(WeavingInternalNames.SYNTHETIC_EXIT_PREFIX) }
    }

    def "visitMethod ignores a pre-existing synthetic enrich method and visitEnd still emits exactly one enrich method"() {
        given:
        def extractor = new FieldExtractorRef("traceId", "com/example/Foo", "getTrace", "()Ljava/lang/String;", false)
        def graph = realGraph([realMethodNode("doWork", "()V")], LogLevel.DEBUG, LogLevel.DEBUG, [extractor])
        def transformer = new AotMethodLoggingTransformer(graph)
        def recordedNames = []
        def recordingCv = recordingClassVisitor(recordedNames)
        def normalMethodDesc = nonBridgeMethodDescStub("doWork", "()V")
        def methods = Stub(MethodList) { iterator() >> { [normalMethodDesc].iterator() } }
        def cv = transformer.wrap(
                typeDescriptionStub(),
                recordingCv,
                null, null, null,
                methods,
                0, 0)

        when:
        cv.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/App", null, "java/lang/Object", null)
        cv.visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        def revisited = cv.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                WeavingInternalNames.SYNTHETIC_ENRICH_METHOD,
                "(Lnet/bytebuddy/asm/Advice\$This;)V", null, null)
        cv.visitEnd()

        then:
        revisited == null
        recordedNames.count(WeavingInternalNames.SYNTHETIC_ENRICH_METHOD) == 1
    }

    private RegistryRouteGraph routeGraphEmpty() {
        realGraph([])
    }

    private RegistryRouteGraph realGraph(List<MethodNode> methodNodes) {
        realGraph(methodNodes, LogLevel.DEBUG, LogLevel.DEBUG, [])
    }

    private RegistryRouteGraph realGraph(
            List<MethodNode> methodNodes,
            LogLevel enterLevel,
            LogLevel exitLevel,
            List<FieldExtractorRef> extractors) {
        realGraphInternal(methodNodes, enterLevel, exitLevel, extractors, CLASS_NAME)
    }

    private RegistryRouteGraph realGraphWithDeclaringSimpleName(
            List<MethodNode> methodNodes, String declaringSimpleName) {
        realGraphInternal(methodNodes, LogLevel.DEBUG, LogLevel.DEBUG, [], declaringSimpleName)
    }

    private RegistryRouteGraph realGraphInternal(
            List<MethodNode> methodNodes,
            LogLevel enterLevel,
            LogLevel exitLevel,
            List<FieldExtractorRef> extractors,
            String declaringSimpleName) {
        def rule = new MethodLoggingRule(
                "test-route", ["sample"], [], ["App"], enterLevel, exitLevel, extractors)
        def metadata = new RegistryMetadata("sample.Registry", 16, [])
        def factory = { graph, type ->
            new TypeNode(
                    "sample",
                    declaringSimpleName,
                    false,
                    false,
                    Family.NONE,
                    Optional.of(rule),
                    Optional.empty(),
                    methodNodes,
                    [],
                    [])
        } as RegistryRouteGraph.TypeNodeFactory
        new RegistryRouteGraph(metadata, [rule], [], factory)
    }

    private MethodNode realMethodNode(String name, String descriptor, boolean shapeEligible = true) {
        new MethodNode(
                name,
                descriptor,
                false,
                shapeEligible,
                Family.NONE,
                Family.NONE,
                Collections.emptyList(),
                Family.NONE,
                false)
    }

    private TypeDescription typeDescriptionStub() {
        Stub(TypeDescription) {
            getSimpleName() >> CLASS_NAME
            getName() >> BINARY_NAME
        }
    }

    private MethodDescription nonBridgeMethodDescStub(String name, String descriptor, Class<?> returnType = void.class) {
        def returnTypeDesc = TypeDescription.ForLoadedType.of(returnType)
        def declaringType = typeDescriptionStub()
        Stub(MethodDescription) {
            isBridge() >> false
            getInternalName() >> name
            getName() >> name
            getDescriptor() >> descriptor
            isStatic() >> false
            getModifiers() >> Opcodes.ACC_PUBLIC
            getParameters() >> new ParameterList.Empty<>()
            // AnnotationList is consumed by downstream ClassPlanAssembler when building the method plan;
            // an empty stub is enough because this spec exercises only transformer-level dispatch.
            getDeclaredAnnotations() >> Stub(AnnotationList) {
                isAnnotationPresent(_) >> false
                ofType(_) >> null
                iterator() >> [].iterator()
            }
            getDeclaringType() >> Stub(TypeDescription.Generic) {
                asErasure() >> declaringType
                getInterfaces() >> Stub(TypeList.Generic) { iterator() >> [].iterator() }
            }
            getReturnType() >> Stub(TypeDescription.Generic) {
                asErasure() >> returnTypeDesc
            }
            asSignatureToken() >> new MethodDescription.SignatureToken(
                    name, returnTypeDesc, Collections.emptyList())
        }
    }

    private List sentinelAndDelegate() {
        def sentinel = new MethodVisitor(Opcodes.ASM9) {}
        def wrappedCv = new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) { sentinel }
        }
        [sentinel, wrappedCv]
    }

    private ClassVisitor recordingClassVisitor(List<String> recordedNames) {
        new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                recordedNames << name
                new MethodVisitor(Opcodes.ASM9) {}
            }
        }
    }
}
