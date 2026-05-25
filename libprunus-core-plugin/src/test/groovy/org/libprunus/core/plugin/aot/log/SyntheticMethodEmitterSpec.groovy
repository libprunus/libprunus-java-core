package org.libprunus.core.plugin.aot.log

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.matcher.ElementMatchers
import spock.lang.Specification

class SyntheticMethodEmitterSpec extends Specification {

    private static final TypeDescription FIXTURE_TYPE = TypeDescription.ForLoadedType.of(Fixture)

    private static MethodDescription fixtureMethod(String name) {
        FIXTURE_TYPE.getDeclaredMethods()
                .filter(ElementMatchers.named(name))
                .getOnly()
    }

    def "private constructor throws UnsupportedOperationException to enforce non-instantiability"() {
        when:
        new SyntheticMethodEmitter()

        then:
        thrown(UnsupportedOperationException)
    }

    def "emitEnrichMethod generates a synthetic method with enrich name, descriptor, and private static synthetic access"() {
        given:
        def recording = new MethodRecordingClassVisitor()
        def extractors = [new FieldExtractorRef("myField", "test/Config", "getMyField", "()Ljava/lang/String;", false)]

        when:
        SyntheticMethodEmitter.emitEnrichMethod(recording, extractors)

        then:
        recording.lastMethodName == WeavingInternalNames.SYNTHETIC_ENRICH_METHOD
        recording.lastMethodDescriptor == AsmDescriptors.ENRICH_METHOD_DESCRIPTOR
        recording.lastMethodAccess == (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC)
    }

    def "emitEnrichMethod forwards extractor interface flag to INVOKESTATIC for both true and false values"() {
        given:
        def capture = new InvokeInterfaceFlagCaptureClassVisitor()
        def extractors = [new FieldExtractorRef("myField", "test/Config", "getMyField", "()Ljava/lang/String;", flagInput)]

        when:
        SyntheticMethodEmitter.emitEnrichMethod(capture, extractors)

        then:
        capture.captured
        capture.lastStaticInvokeInterfaceFlag == flagInput

        where:
        flagInput << [true, false]
    }

    def "emitEnrichMethod with no extractors emits only ALOAD0 and ARETURN"() {
        given:
        def instructions = []
        def cv = new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitVarInsn(int op, int v) { instructions << ['var', op, v] }
                    @Override
                    void visitInsn(int op) { instructions << ['insn', op] }
                    @Override
                    void visitLdcInsn(Object value) { instructions << ['ldc', value] }
                    @Override
                    void visitMethodInsn(int op, String owner, String n, String d, boolean i) {
                        instructions << ['method', op, owner, n]
                    }
                }
            }
        }

        when:
        SyntheticMethodEmitter.emitEnrichMethod(cv, [])

        then:
        instructions.findAll { it[0] == 'ldc' }.size() == 0
        instructions.findAll { it[0] == 'method' }.size() == 0
        instructions.findAll { it[0] == 'var' && it[1] == Opcodes.ALOAD && it[2] == 0 }.size() == 1
        instructions.findAll { it[0] == 'insn' && it[1] == Opcodes.ARETURN }.size() == 1
    }

    def "emitEnrichMethod emits multiple extractors in input list order"() {
        given:
        def capture = new EnrichLdcOrderCapturingClassVisitor()
        def extractors = [
                new FieldExtractorRef("alpha", "test/Config", "getAlpha", "()Ljava/lang/String;", false),
                new FieldExtractorRef("beta", "test/Config", "getBeta", "()Ljava/lang/String;", false),
                new FieldExtractorRef("gamma", "test/Config", "getGamma", "()Ljava/lang/String;", false),
        ]

        when:
        SyntheticMethodEmitter.emitEnrichMethod(capture, extractors)

        then:
        capture.ldcFieldNames == ["alpha", "beta", "gamma"]
        capture.invokedMethodNames == ["getAlpha", "addKeyValue", "getBeta", "addKeyValue", "getGamma", "addKeyValue"]
    }

    def "emitEnrichMethod fails fast when extractor signature is invalid and leaves the method incomplete"() {
        given:
        def recording = new MethodCompletionRecordingClassVisitor()

        when:
        SyntheticMethodEmitter.emitEnrichMethod(
                recording,
                [new FieldExtractorRef("bad", "sample/synthetic/InvalidEnrich", "badExtractor", invalidDescriptor, false)])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Field extractor must be a no-arg method and cannot return void")
        ex.message.contains("sample/synthetic/InvalidEnrich#badExtractor")
        recording.visitEndCount == 0
        recording.visitMaxsCount == 0

        where:
        invalidCase             | invalidDescriptor
        "void return"           | "()V"
        "non-zero argument"     | "(Ljava/lang/String;)Ljava/lang/Object;"
    }

    def "buildSyntheticEnterDescriptor starts with Logger argument followed by method parameter types"() {
        given:
        def method = fixtureMethod(methodName)

        when:
        def descriptor = SyntheticMethodEmitter.buildSyntheticEnterDescriptor(method)

        then:
        descriptor == expected

        where:
        methodName            || expected
        "voidNoArgs"          || "(Lorg/slf4j/Logger;)V"
        "intParam"            || "(Lorg/slf4j/Logger;I)V"
        "multiParams"         || "(Lorg/slf4j/Logger;Ljava/lang/String;ID)V"
        "staticIntReturn"     || "(Lorg/slf4j/Logger;I)V"
        "longReturn"          || "(Lorg/slf4j/Logger;D)V"
    }

    def "buildSyntheticExitDescriptor includes return type for non-void and Logger only for void"() {
        given:
        def method = fixtureMethod(methodName)
        def returnType = Type.getReturnType(method.getDescriptor())

        when:
        def descriptor = SyntheticMethodEmitter.buildSyntheticExitDescriptor(returnType)

        then:
        descriptor == expected

        where:
        methodName        || expected
        "voidNoArgs"      || "(Lorg/slf4j/Logger;)V"
        "intParam"        || "(Lorg/slf4j/Logger;)V"
        "staticIntReturn" || "(Lorg/slf4j/Logger;I)V"
        "longReturn"      || "(Lorg/slf4j/Logger;J)V"
        "objectReturn"    || "(Lorg/slf4j/Logger;Ljava/lang/String;)V"
    }

    def "emitAutoboxing emits wrapper-specific valueOf with primitive-to-wrapper descriptor for primitives and no-op for reference and void and method sorts"() {
        given:
        def recording = new BoxingRecordingMethodVisitor()

        when:
        SyntheticMethodEmitter."emitAutoboxing"(recording, type)

        then:
        recording.boxingCall == expectedBoxing

        where:
        type                       || expectedBoxing
        Type.BOOLEAN_TYPE          || "java/lang/Boolean.valueOf(Z)Ljava/lang/Boolean;"
        Type.BYTE_TYPE             || "java/lang/Byte.valueOf(B)Ljava/lang/Byte;"
        Type.CHAR_TYPE             || "java/lang/Character.valueOf(C)Ljava/lang/Character;"
        Type.SHORT_TYPE            || "java/lang/Short.valueOf(S)Ljava/lang/Short;"
        Type.INT_TYPE              || "java/lang/Integer.valueOf(I)Ljava/lang/Integer;"
        Type.LONG_TYPE             || "java/lang/Long.valueOf(J)Ljava/lang/Long;"
        Type.FLOAT_TYPE            || "java/lang/Float.valueOf(F)Ljava/lang/Float;"
        Type.DOUBLE_TYPE           || "java/lang/Double.valueOf(D)Ljava/lang/Double;"
        Type.getType(String)       || null
        Type.getType(Object)       || null
        Type.VOID_TYPE             || null
        Type.getType("()V")        || null
    }

    private static class BoxingRecordingMethodVisitor extends MethodVisitor {
        String boxingCall = null

        BoxingRecordingMethodVisitor() { super(Opcodes.ASM9) }

        @Override
        void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC && name == "valueOf") {
                boxingCall = "$owner.$name$descriptor"
            }
        }
    }

    private static class MethodRecordingClassVisitor extends ClassVisitor {
        int lastMethodAccess = -1
        String lastMethodName = null
        String lastMethodDescriptor = null

        MethodRecordingClassVisitor() { super(Opcodes.ASM9) }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            lastMethodAccess = access
            lastMethodName = name
            lastMethodDescriptor = descriptor
            new MethodVisitor(Opcodes.ASM9) {}
        }
    }

    private static class MethodCompletionRecordingClassVisitor extends ClassVisitor {
        int visitEndCount = 0
        int visitMaxsCount = 0

        MethodCompletionRecordingClassVisitor() { super(Opcodes.ASM9) }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            new MethodVisitor(Opcodes.ASM9) {
                @Override
                void visitEnd() { visitEndCount++ }
                @Override
                void visitMaxs(int maxStack, int maxLocals) { visitMaxsCount++ }
            }
        }
    }

    private static class InvokeInterfaceFlagCaptureClassVisitor extends ClassVisitor {
        boolean captured = false
        boolean lastStaticInvokeInterfaceFlag = false

        InvokeInterfaceFlagCaptureClassVisitor() { super(Opcodes.ASM9) }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            new MethodVisitor(Opcodes.ASM9) {
                @Override
                void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                    if (opcode == Opcodes.INVOKESTATIC && owner == "test/Config" && methodName == "getMyField") {
                        captured = true
                        lastStaticInvokeInterfaceFlag = isInterface
                    }
                }
            }
        }
    }

    private static class EnrichLdcOrderCapturingClassVisitor extends ClassVisitor {
        final List<String> ldcFieldNames = []
        final List<String> invokedMethodNames = []

        EnrichLdcOrderCapturingClassVisitor() { super(Opcodes.ASM9) }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            new MethodVisitor(Opcodes.ASM9) {
                @Override
                void visitLdcInsn(Object value) {
                    if (value instanceof String) {
                        ldcFieldNames << ((String) value)
                    }
                }

                @Override
                void visitMethodInsn(int opcode, String owner, String mName, String mDescriptor, boolean isInterface) {
                    invokedMethodNames << mName
                }
            }
        }
    }

    @SuppressWarnings("unused")
    static class Fixture {
        void voidNoArgs() {}

        void intParam(int x) {}

        void multiParams(String s, int i, double d) {}

        static int staticIntReturn(int x) { return x }

        long longReturn(double d) { return 0L }

        String objectReturn() { return "" }
    }
}
