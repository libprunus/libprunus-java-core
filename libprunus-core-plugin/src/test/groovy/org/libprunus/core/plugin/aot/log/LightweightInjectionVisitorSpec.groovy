package org.libprunus.core.plugin.aot.log

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.jar.asm.AnnotationVisitor
import net.bytebuddy.jar.asm.ConstantDynamic
import net.bytebuddy.jar.asm.Label
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.jar.asm.TypePath
import net.bytebuddy.matcher.ElementMatchers
import org.libprunus.core.log.runtime.LogLevel
import spock.lang.Specification

class LightweightInjectionVisitorSpec extends Specification {

    private static final TypeDescription FIXTURE_TYPE = TypeDescription.ForLoadedType.of(Fixture)

    private static MethodDescription fixtureMethod(String name) {
        FIXTURE_TYPE.getDeclaredMethods()
                .filter(ElementMatchers.named(name))
                .getOnly()
    }

    private static LightweightInjectionVisitor createVisitor(
            MethodVisitor mv, String methodName, LogLevel enter, LogLevel exit, boolean returnIgnored = false) {
        def method = fixtureMethod(methodName)
        new LightweightInjectionVisitor(mv, method, "test/Fixture", enter, exit, "", returnIgnored)
    }

    private static LightweightInjectionVisitor createVisitorWithClassInternalName(
            MethodVisitor mv, String methodName, LogLevel enter, LogLevel exit, String classInternalName) {
        def method = fixtureMethod(methodName)
        new LightweightInjectionVisitor(mv, method, classInternalName, enter, exit, "", false)
    }

    def "should map plan slot allocation into visitor fields across representative LogLevel and method-shape combinations"() {
        given:
        def visitor = createVisitor(new MethodVisitor(Opcodes.ASM9) {}, methodName, enter, exit)

        expect:
        visitor.@shiftAmount == expectedShift
        visitor.@returnValueSlot == expectedReturnSlot
        visitor.@loggerSlot == expectedLoggerSlot

        and:
        def shiftedUserStart = visitor.@firstLocal + visitor.@shiftAmount
        (visitor.@returnValueSlot == -1 || visitor.@returnValueSlot < shiftedUserStart)
        (visitor.@loggerSlot == -1 || visitor.@loggerSlot < shiftedUserStart)

        where:
        methodName          | enter          | exit           || expectedShift | expectedReturnSlot | expectedLoggerSlot
        "voidNoArgs"        | LogLevel.OFF   | LogLevel.OFF   || 0             | -1                 | -1
        "voidNoArgs"        | LogLevel.INFO  | LogLevel.OFF   || 1             | -1                 | 1
        "voidNoArgs"        | LogLevel.OFF   | LogLevel.INFO  || 1             | -1                 | 1
        "voidNoArgs"        | LogLevel.INFO  | LogLevel.INFO  || 1             | -1                 | 1
        "instanceReturnInt" | LogLevel.INFO  | LogLevel.INFO  || 2             | 3                  | 4
        "instanceReturnInt" | LogLevel.OFF   | LogLevel.INFO  || 2             | 3                  | 4
        "instanceReturnInt" | LogLevel.INFO  | LogLevel.OFF   || 1             | -1                 | 3
        "wideReturn"        | LogLevel.INFO  | LogLevel.INFO  || 3             | 3                  | 5
        "wideReturn"        | LogLevel.OFF   | LogLevel.INFO  || 3             | 3                  | 5
        "staticMethod"      | LogLevel.INFO  | LogLevel.INFO  || 2             | 1                  | 2
    }

    def "visitCode emits INVOKESTATIC isEnabled guard and INVOKESTATIC to synthetic enter method"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "voidNoArgs", LogLevel.INFO, LogLevel.OFF)

        when:
        visitor.visitCode()

        then:
        recording.staticCalls.any {
            it.owner == WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME &&
                it.name == "isEnabled" &&
                it.descriptor == "()Z"
        }

        and:
        recording.staticCalls.any { it.name == WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "voidNoArgs" }
    }

    def "visitCode emits no instructions when enter level is OFF"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "voidNoArgs", LogLevel.OFF, LogLevel.OFF)

        when:
        visitor.visitCode()

        then:
        recording.staticCalls.isEmpty()
    }

    def "visitVarInsn shifts var index when at or above firstLocal and shiftAmount is positive"() {
        given:
        def capturing = new CapturingMethodVisitor()
        def visitor = createVisitor(capturing, "instanceReturnInt", LogLevel.INFO, LogLevel.INFO)

        when:
        visitor.visitVarInsn(Opcodes.ALOAD, inputVar)

        then:
        capturing.lastVar == expectedVar

        where:
        inputVar || expectedVar
        0        || 0
        2        || 2
        3        || 5
        4        || 6
    }

    def "visitVarInsn passes var unchanged when shiftAmount is zero"() {
        given:
        def capturing = new CapturingMethodVisitor()
        def visitor = createVisitor(capturing, "instanceReturnInt", LogLevel.OFF, LogLevel.OFF)

        when:
        visitor.visitVarInsn(Opcodes.ALOAD, 3)

        then:
        capturing.lastVar == 3
    }

    def "visitIincInsn shifts var when at or above firstLocal and shiftAmount is positive"() {
        given:
        def capturing = new CapturingMethodVisitor()
        def visitor = createVisitor(capturing, "instanceReturnInt", LogLevel.INFO, LogLevel.INFO)

        when:
        visitor.visitIincInsn(inputVar, 1)

        then:
        capturing.lastIincVar == expectedVar

        where:
        inputVar || expectedVar
        0        || 0
        2        || 2
        3        || 5
    }

    def "visitIincInsn passes var unchanged when shiftAmount is zero"() {
        given:
        def capturing = new CapturingMethodVisitor()
        def visitor = createVisitor(capturing, "instanceReturnInt", LogLevel.OFF, LogLevel.OFF)

        when:
        visitor.visitIincInsn(5, 1)

        then:
        capturing.lastIincVar == 5
    }

    def "visitLocalVariable shifts index when at or above firstLocal and shiftAmount is positive"() {
        given:
        def capturing = new CapturingMethodVisitor()
        def visitor = createVisitor(capturing, "instanceReturnInt", LogLevel.INFO, LogLevel.INFO)

        when:
        visitor.visitLocalVariable("x", "I", null, new Label(), new Label(), inputIndex)

        then:
        capturing.lastLocalVarIndex == expectedIndex

        where:
        inputIndex || expectedIndex
        0          || 0
        2          || 2
        3          || 5
    }

    def "visitLocalVariable passes index unchanged when shiftAmount is zero"() {
        given:
        def capturing = new CapturingMethodVisitor()
        def visitor = createVisitor(capturing, "instanceReturnInt", LogLevel.OFF, LogLevel.OFF)

        when:
        visitor.visitLocalVariable("x", "I", null, new Label(), new Label(), 3)

        then:
        capturing.lastLocalVarIndex == 3
    }

    def "visitLocalVariableAnnotation produces shifted index array when shiftAmount is positive"() {
        given:
        def capturing = new CapturingMethodVisitor()
        def visitor = createVisitor(capturing, "instanceReturnInt", LogLevel.INFO, LogLevel.INFO)

        when:
        visitor.visitLocalVariableAnnotation(
                0x40 << 24, null, new Label[0], new Label[0], [1, 3] as int[], "Ljava/lang/String;", true)

        then:
        capturing.lastAnnotationIndex == [1, 5] as int[]
    }

    def "visitLocalVariableAnnotation passes original index array when shiftAmount is zero"() {
        given:
        def capturing = new CapturingMethodVisitor()
        def visitor = createVisitor(capturing, "instanceReturnInt", LogLevel.OFF, LogLevel.OFF)
        def originalIndex = [3, 4] as int[]

        when:
        visitor.visitLocalVariableAnnotation(
                0x40 << 24, null, new Label[0], new Label[0], originalIndex, "Ljava/lang/String;", true)

        then:
        capturing.lastAnnotationIndex.is(originalIndex)
    }

    def "visitInsn with RETURN jumps to exit epilogue when exit logging is enabled"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "voidNoArgs", LogLevel.OFF, LogLevel.INFO)

        when:
        visitor.visitInsn(Opcodes.RETURN)

        then:
        recording.jumpOpcodes.contains(Opcodes.GOTO)
        !recording.insns.contains(Opcodes.RETURN)
    }

    def "visitInsn with typed return stores value and jumps to exit epilogue when exit logging is enabled"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "intReturn", LogLevel.OFF, LogLevel.INFO)

        when:
        visitor.visitInsn(Opcodes.IRETURN)

        then:
        recording.varInsns.any { it.opcode == Opcodes.ISTORE }
        recording.jumpOpcodes.contains(Opcodes.GOTO)
        !recording.insns.contains(Opcodes.IRETURN)
    }

    def "should store return value and jump to exit epilogue for every typed return opcode"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, methodName, LogLevel.OFF, LogLevel.INFO)

        when:
        visitor.visitInsn(returnOpcode)

        then:
        recording.varInsns.any { it.opcode == expectedStoreOpcode }
        recording.jumpOpcodes.contains(Opcodes.GOTO)
        !recording.insns.contains(returnOpcode)

        where:
        methodName          | returnOpcode      | expectedStoreOpcode
        "intReturn"         | Opcodes.IRETURN   | Opcodes.ISTORE
        "wideReturn"        | Opcodes.LRETURN   | Opcodes.LSTORE
        "floatReturn"       | Opcodes.FRETURN   | Opcodes.FSTORE
        "doubleReturn"      | Opcodes.DRETURN   | Opcodes.DSTORE
        "instanceReturnInt" | Opcodes.ARETURN   | Opcodes.ASTORE
    }

    def "should bypass return interception and skip exit epilogue when exit log level is OFF"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "voidNoArgs", LogLevel.OFF, LogLevel.OFF)

        when:
        visitor.visitInsn(Opcodes.RETURN)
        visitor.visitMaxs(10, 10)

        then:
        recording.insns.contains(Opcodes.RETURN)
        recording.jumpOpcodes.isEmpty()
        recording.staticCalls.isEmpty()
        recording.varInsns.findAll { it.opcode == Opcodes.ISTORE }.isEmpty()
    }

    def "visitInsn with typed return passes through unchanged when exit log level is OFF"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, methodName, LogLevel.OFF, LogLevel.OFF)

        when:
        visitor.visitInsn(returnOpcode)

        then:
        recording.insns.contains(returnOpcode)
        recording.jumpOpcodes.isEmpty()
        recording.varInsns.findAll { it.opcode == expectedStoreOpcode }.isEmpty()

        where:
        methodName     | returnOpcode      | expectedStoreOpcode
        "intReturn"    | Opcodes.IRETURN   | Opcodes.ISTORE
        "wideReturn"   | Opcodes.LRETURN   | Opcodes.LSTORE
        "floatReturn"  | Opcodes.FRETURN   | Opcodes.FSTORE
        "doubleReturn" | Opcodes.DRETURN   | Opcodes.DSTORE
    }

    def "visitMaxs emits exit epilogue with INVOKESTATIC guards when exit is enabled and return was intercepted"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "instanceReturnInt", LogLevel.OFF, LogLevel.INFO)
        visitor.visitInsn(Opcodes.IRETURN)
        recording.clear()

        when:
        visitor.visitMaxs(10, 10)

        then:
        recording.staticCalls.any {
            it.owner == WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME &&
                it.name == "isEnabled" &&
                it.descriptor == "()Z"
        }
        recording.staticCalls.any { it.name == WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "instanceReturnInt" }
    }

    def "visitMaxs does not emit exit epilogue when no return was intercepted"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "instanceReturnInt", LogLevel.OFF, LogLevel.INFO)

        when:
        visitor.visitMaxs(10, 10)

        then:
        recording.staticCalls.isEmpty()
    }

    def "exit epilogue omits return value load before synthetic exit call when returnIgnored is true"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "intReturn", LogLevel.OFF, LogLevel.INFO, true)
        visitor.visitInsn(Opcodes.IRETURN)
        recording.clear()

        when:
        visitor.visitMaxs(10, 10)

        then:
        recording.varInsns.count { it.opcode == Opcodes.ILOAD } == 1
        recording.insns.contains(Opcodes.IRETURN)
    }

    def "exit epilogue emits two ILOAD instructions when returnIgnored is false"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "intReturn", LogLevel.OFF, LogLevel.INFO)
        visitor.visitInsn(Opcodes.IRETURN)
        recording.clear()

        when:
        visitor.visitMaxs(10, 10)

        then:
        recording.varInsns.count { it.opcode == Opcodes.ILOAD } == 2
        recording.insns.contains(Opcodes.IRETURN)
    }

    def "should preserve return value through returnValueSlot via matching ISTORE and ILOAD instructions for typed returns"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, methodName, LogLevel.OFF, LogLevel.INFO, returnIgnored)
        def slot = visitor.@returnValueSlot

        when:
        visitor.visitInsn(typedReturnOpcode)
        visitor.visitMaxs(10, 10)

        then:
        def stores = recording.varInsns.findAll { it.opcode == storeOpcode }
        def loads = recording.varInsns.findAll { it.opcode == loadOpcode }
        stores.size() == 1
        stores[0].var == slot
        loads.size() == expectedLoadCount
        loads.every { it.var == slot }
        recording.insns.last() == typedReturnOpcode

        where:
        methodName   | typedReturnOpcode | storeOpcode    | loadOpcode    | returnIgnored || expectedLoadCount
        "intReturn"  | Opcodes.IRETURN   | Opcodes.ISTORE | Opcodes.ILOAD | false         || 2
        "intReturn"  | Opcodes.IRETURN   | Opcodes.ISTORE | Opcodes.ILOAD | true          || 1
        "wideReturn" | Opcodes.LRETURN   | Opcodes.LSTORE | Opcodes.LLOAD | false         || 2
        "wideReturn" | Opcodes.LRETURN   | Opcodes.LSTORE | Opcodes.LLOAD | true          || 1
    }

    def "exit epilogue emits final RETURN opcode when return type is void"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "voidNoArgs", LogLevel.OFF, LogLevel.INFO)
        visitor.visitInsn(Opcodes.RETURN)
        recording.clear()

        when:
        visitor.visitMaxs(10, 10)

        then:
        recording.insns.last() == Opcodes.RETURN
        recording.varInsns.findAll { it.opcode == Opcodes.ILOAD }.isEmpty()
    }

    def "should call Logger isXxxEnabled matching the configured level on both enter guard and exit epilogue"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "intReturn", enter, exit)

        when:
        visitor.visitCode()
        visitor.visitInsn(Opcodes.IRETURN)
        visitor.visitMaxs(10, 10)

        then:
        def enterMatches = recording.interfaceCalls.findAll {
            it.owner == AsmDescriptors.LOGGER_INTERNAL_NAME && it.name == expectedEnterCheck
        }
        def exitMatches = recording.interfaceCalls.findAll {
            it.owner == AsmDescriptors.LOGGER_INTERNAL_NAME && it.name == expectedExitCheck
        }
        enterMatches.size() == expectedEnterCount
        exitMatches.size() == expectedExitCount

        where:
        enter          | exit           || expectedEnterCheck | expectedExitCheck | expectedEnterCount | expectedExitCount
        LogLevel.TRACE | LogLevel.WARN  || "isTraceEnabled"   | "isWarnEnabled"   | 1                  | 1
        LogLevel.DEBUG | LogLevel.ERROR || "isDebugEnabled"   | "isErrorEnabled"  | 1                  | 1
        LogLevel.INFO  | LogLevel.INFO  || "isInfoEnabled"    | "isInfoEnabled"   | 2                  | 2
        LogLevel.OFF   | LogLevel.INFO  || "isTraceEnabled"   | "isInfoEnabled"   | 0                  | 1
        LogLevel.INFO  | LogLevel.OFF   || "isInfoEnabled"    | "isTraceEnabled"  | 1                  | 0
    }

    def "emitLoggerGuardAndInvoke checks LogRuntime.isEnabled before Logger isXxxEnabled in the enter guard"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "voidNoArgs", LogLevel.INFO, LogLevel.OFF)

        when:
        visitor.visitCode()

        then:
        def runtimeGuardPos = recording.methodCalls.findIndexOf {
            it.opcode == Opcodes.INVOKESTATIC &&
                it.owner == WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME &&
                it.name == "isEnabled"
        }
        def loggerGuardPos = recording.methodCalls.findIndexOf {
            it.opcode == Opcodes.INVOKEINTERFACE &&
                it.owner == AsmDescriptors.LOGGER_INTERNAL_NAME &&
                it.name == "isInfoEnabled"
        }
        runtimeGuardPos >= 0
        loggerGuardPos >= 0
        runtimeGuardPos < loggerGuardPos
    }

    def "should push original parameters starting at slot 1 for instance methods and advance two slots for wide-typed parameters"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "wideReturn", LogLevel.INFO, LogLevel.OFF)

        when:
        visitor.visitCode()

        then:
        def doubleLoads = recording.varInsns.findAll { it.opcode == Opcodes.DLOAD }
        doubleLoads.size() == 1
        doubleLoads[0].var == 1

        and:
        !recording.varInsns.any { it.opcode == Opcodes.DLOAD && it.var == 0 }
        !recording.varInsns.any { it.opcode == Opcodes.ILOAD && it.var == 0 }
    }

    def "pushOriginalParameters starts at slot 0 for static methods"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "staticMethod", LogLevel.INFO, LogLevel.OFF)

        when:
        visitor.visitCode()

        then:
        def intLoads = recording.varInsns.findAll { it.opcode == Opcodes.ILOAD }
        intLoads.size() == 1
        intLoads[0].var == 0
    }

    def "pushOriginalParameters emits no parameter loads for instance method with no parameters"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "voidNoArgs", LogLevel.INFO, LogLevel.OFF)
        def loggerSlot = visitor.@loggerSlot

        when:
        visitor.visitCode()

        then:
        !recording.varInsns.any {
            it.var != loggerSlot && it.opcode in [Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD]
        }
    }

    def "should emit LDC ConstantDynamic AOT_LOGGER with condyLoggerFactory bootstrap when enter logging is enabled"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitor(recording, "voidNoArgs", LogLevel.INFO, LogLevel.OFF)

        when:
        visitor.visitCode()

        then:
        def condy = recording.ldcArgs.find { it instanceof ConstantDynamic } as ConstantDynamic
        condy.name == "AOT_LOGGER"
        condy.descriptor == Type.getObjectType(AsmDescriptors.LOGGER_INTERNAL_NAME).getDescriptor()
        condy.bootstrapMethod.owner == WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME
        condy.bootstrapMethod.name == "condyLoggerFactory"

        and:
        condy.bootstrapMethod.tag == Opcodes.H_INVOKESTATIC
        condy.bootstrapMethod.desc == "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/String;)Lorg/slf4j/Logger;"

        and:
        recording.invokeDynamicNames.findAll { it == "isEnabled" }.isEmpty()
    }

    def "emitLoggerConstant passes binary class name as condy bootstrap static argument"() {
        given:
        def recording = new RecordingMethodVisitor()
        def visitor = createVisitorWithClassInternalName(
                recording, "voidNoArgs", LogLevel.INFO, LogLevel.OFF, "org/example/MyService")

        when:
        visitor.visitCode()

        then:
        def condy = recording.ldcArgs.find { it instanceof ConstantDynamic } as ConstantDynamic
        condy.bootstrapMethodArgumentCount == 1
        condy.getBootstrapMethodArgument(0) == "org.example.MyService"
    }

    private static class CapturingMethodVisitor extends MethodVisitor {
        int lastVar = Integer.MIN_VALUE
        int lastIincVar = Integer.MIN_VALUE
        int lastLocalVarIndex = Integer.MIN_VALUE
        int[] lastAnnotationIndex = null

        CapturingMethodVisitor() { super(Opcodes.ASM9) }

        @Override
        void visitVarInsn(int opcode, int var) { lastVar = var }

        @Override
        void visitIincInsn(int var, int increment) { lastIincVar = var }

        @Override
        void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
            lastLocalVarIndex = index
        }

        @Override
        AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end,
                                                       int[] index, String descriptor, boolean visible) {
            lastAnnotationIndex = index
            null
        }
    }

    private static class RecordingMethodVisitor extends MethodVisitor {
        List<String> invokeDynamicNames = []
        List<Map> staticCalls = []
        List<Map> interfaceCalls = []
        List<Map> methodCalls = []
        List<Map> varInsns = []
        List<Integer> insns = []
        List<Integer> jumpOpcodes = []
        List<Object> ldcArgs = []

        RecordingMethodVisitor() { super(Opcodes.ASM9) }

        @Override
        void visitInvokeDynamicInsn(String name, String descriptor,
                                    net.bytebuddy.jar.asm.Handle bsm, Object... bsmArgs) {
            invokeDynamicNames << name
        }

        @Override
        void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            methodCalls << [opcode: opcode, owner: owner, name: name, descriptor: descriptor]
            if (opcode == Opcodes.INVOKESTATIC) {
                staticCalls << [owner: owner, name: name, descriptor: descriptor]
            } else if (opcode == Opcodes.INVOKEINTERFACE) {
                interfaceCalls << [owner: owner, name: name, descriptor: descriptor]
            }
        }

        @Override
        void visitVarInsn(int opcode, int var) {
            varInsns << [opcode: opcode, var: var]
        }

        @Override
        void visitInsn(int opcode) {
            insns << opcode
        }

        @Override
        void visitJumpInsn(int opcode, Label label) {
            jumpOpcodes << opcode
        }

        @Override
        void visitLabel(Label label) {}

        @Override
        void visitLdcInsn(Object value) {
            ldcArgs << value
        }

        @Override
        void visitMaxs(int maxStack, int maxLocals) {}

        void clear() {
            invokeDynamicNames.clear()
            staticCalls.clear()
            interfaceCalls.clear()
            methodCalls.clear()
            varInsns.clear()
            insns.clear()
            jumpOpcodes.clear()
            ldcArgs.clear()
        }
    }

    static class Fixture {
        void voidNoArgs() {}

        String instanceReturnInt(int a, int b) { "" }

        int intReturn(int x) { return x }

        long wideReturn(double d) { return 0L }

        float floatReturn() { 0f }

        double doubleReturn() { 0d }

        static int staticMethod(int x) { return x }
    }
}
