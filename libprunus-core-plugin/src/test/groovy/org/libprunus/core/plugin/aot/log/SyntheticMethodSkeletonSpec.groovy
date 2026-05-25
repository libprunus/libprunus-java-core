package org.libprunus.core.plugin.aot.log

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.ClassWriter
import net.bytebuddy.jar.asm.Label
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.matcher.ElementMatchers
import org.libprunus.core.log.runtime.LogLevel
import org.libprunus.core.plugin.aot.log.AotMethodLoggingTransformer.MethodLogContext
import spock.lang.Specification

class SyntheticMethodSkeletonSpec extends Specification {

    private static final TypeDescription FIXTURE_TYPE = TypeDescription.ForLoadedType.of(Fixture)

    private static MethodDescription fixtureMethod(String name) {
        FIXTURE_TYPE.getDeclaredMethods()
                .filter(ElementMatchers.named(name))
                .getOnly()
    }

    private static ClassPlanAssembler.MethodPlan defaultMethodPlan(
            MethodDescription method, LogLevel enterLevel, LogLevel exitLevel) {
        int bitsetLength = (method.getParameters().size() + Long.SIZE - 1) >>> 6
        def methodKey = new ClassPlanAssembler.MethodKey(
                method.getDeclaringType().asErasure().getInternalName(),
                method.getInternalName(),
                method.getDescriptor())
        new ClassPlanAssembler.MethodPlan(
                methodKey, new long[bitsetLength], new long[bitsetLength], false, false, enterLevel, exitLevel)
    }

    def "private constructor throws UnsupportedOperationException to enforce non-instantiability"() {
        when:
        new SyntheticMethodSkeleton()

        then:
        thrown(UnsupportedOperationException)
    }

    def "fqcnForHandler joins class internal name (dot-converted), '#', method internal name and descriptor"() {
        given:
        def method = fixtureMethod(methodName)

        when:
        def fqcn = SyntheticMethodSkeleton.fqcnForHandler(classInternalName, method)

        then:
        fqcn == expected

        where:
        classInternalName            | methodName     || expected
        "com/example/App"            | "voidNoArgs"   || "com.example.App#voidNoArgs()V"
        "com/example/Outer\$Inner"   | "intParam"     || 'com.example.Outer$Inner#intParam(I)V'
        ""                           | "voidNoArgs"   || "#voidNoArgs()V"
        "p/q/r/Deep"                 | "intParam"     || "p.q.r.Deep#intParam(I)V"
    }

    def "emitAtLevel emits ALOAD 0 followed by INVOKEINTERFACE on Logger for the fluent at-level method matching the LogLevel"() {
        given:
        def recording = new InstructionRecordingMethodVisitor()

        when:
        SyntheticMethodSkeleton.emitAtLevel(recording, level)

        then:
        recording.aloadVars == [0]
        recording.invokeInterfaceCalls.size() == 1
        recording.invokeInterfaceCalls[0].owner == "org/slf4j/Logger"
        recording.invokeInterfaceCalls[0].name == expectedFluentName
        recording.invokeInterfaceCalls[0].descriptor == "()Lorg/slf4j/spi/LoggingEventBuilder;"

        and:
        recording.invokeStaticCalls.isEmpty()
        recording.invokeVirtualCalls.isEmpty()

        where:
        level           || expectedFluentName
        LogLevel.TRACE  || "atTrace"
        LogLevel.DEBUG  || "atDebug"
        LogLevel.INFO   || "atInfo"
        LogLevel.WARN   || "atWarn"
        LogLevel.ERROR  || "atError"
    }

    def "emitAtLevel propagates IllegalStateException when level is OFF"() {
        given:
        def recording = new InstructionRecordingMethodVisitor()

        when:
        SyntheticMethodSkeleton.emitAtLevel(recording, LogLevel.OFF)

        then:
        thrown(IllegalStateException)

        and:
        recording.invokeInterfaceCalls.isEmpty()
        recording.invokeStaticCalls.isEmpty()
        recording.invokeVirtualCalls.isEmpty()
    }

    def "emitEnrichInvocation emits ALOAD eventSlot, INVOKESTATIC class enrich method, then ASTORE eventSlot"() {
        given:
        def recording = new InstructionRecordingMethodVisitor()
        def eventSlot = 4

        when:
        SyntheticMethodSkeleton.emitEnrichInvocation(recording, "com/example/Target", eventSlot)

        then:
        recording.aloadVars == [eventSlot]
        recording.astoreVars == [eventSlot]
        recording.invokeStaticCalls.size() == 1
        recording.invokeStaticCalls[0].owner == "com/example/Target"
        recording.invokeStaticCalls[0].name == '$lp$enrich'
        recording.invokeStaticCalls[0].descriptor == "(Lorg/slf4j/spi/LoggingEventBuilder;)Lorg/slf4j/spi/LoggingEventBuilder;"

        and:
        recording.invokeInterfaceCalls.isEmpty()
        recording.invokeVirtualCalls.isEmpty()
    }

    def "emitMarkRenderTruncationIfTruncated emits isTruncated, IFEQ to skip label, markRenderTruncation, then skip label"() {
        given:
        def recording = new InstructionRecordingMethodVisitor()
        def contextSlot = 5

        when:
        SyntheticMethodSkeleton.emitMarkRenderTruncationIfTruncated(recording, contextSlot)

        then:
        def truncatedCallIdx = recording.timeline.findIndexOf {
            it.kind == 'method' && it.name == 'isTruncated' && it.opcode == Opcodes.INVOKEVIRTUAL
        }
        def ifeqIdx = recording.timeline.findIndexOf { it.kind == 'jump' && it.opcode == Opcodes.IFEQ }
        def markIdx = recording.timeline.findIndexOf {
            it.kind == 'method' && it.name == 'markRenderTruncation' && it.opcode == Opcodes.INVOKEVIRTUAL
        }
        def skipLabelIdx = recording.timeline.findLastIndexOf { it.kind == 'label' }

        truncatedCallIdx >= 0
        ifeqIdx > truncatedCallIdx
        markIdx > ifeqIdx
        skipLabelIdx > markIdx

        and:
        recording.aloadVars == [contextSlot, contextSlot]
        recording.astoreVars.isEmpty()
        recording.ldcValues.isEmpty()
        recording.invokeStaticCalls.isEmpty()
        recording.invokeInterfaceCalls.isEmpty()
        recording.tryCatchBlocks.isEmpty()
        recording.invokeVirtualCalls.size() == 2
        recording.invokeVirtualCalls[0].name == "isTruncated"
        recording.invokeVirtualCalls[1].name == "markRenderTruncation"
    }

    def "logAndReleaseContext emits ALOAD contextSlot, ALOAD eventSlot, INVOKEVIRTUAL logAndRelease on context"() {
        given:
        def recording = new InstructionRecordingMethodVisitor()
        def contextSlot = 3
        def eventSlot = 4

        when:
        SyntheticMethodSkeleton.logAndReleaseContext(recording, contextSlot, eventSlot)

        then:
        recording.aloadVars == [contextSlot, eventSlot]
        recording.invokeVirtualCalls.size() == 1
        recording.invokeVirtualCalls[0].owner == "org/libprunus/core/log/runtime/StringBuilderWithContext"
        recording.invokeVirtualCalls[0].name == "logAndRelease"
        recording.invokeVirtualCalls[0].descriptor == "(Lorg/slf4j/spi/LoggingEventBuilder;)V"

        and:
        recording.invokeStaticCalls.isEmpty()
        recording.invokeInterfaceCalls.isEmpty()
    }

    def "emitWithErrorBoundary opens with ACONST_NULL + ASTORE eventSlot then ACONST_NULL + ASTORE contextSlot before any try-block instructions"() {
        given:
        def recording = new InstructionRecordingMethodVisitor()
        def method = fixtureMethod("intParam")
        def eventSlot = 4
        def contextSlot = 5
        def body = { mv, e, c -> } as SyntheticMethodSkeleton.BodyEmitter

        when:
        SyntheticMethodSkeleton.emitWithErrorBoundary(
                recording, "com/example/Target", method, LogLevel.INFO, eventSlot, contextSlot, 6, [], body)

        then:
        def initInsns = recording.timeline.take(4)
        initInsns[0].kind == 'insn' && initInsns[0].opcode == Opcodes.ACONST_NULL
        initInsns[1].kind == 'var' && initInsns[1].opcode == Opcodes.ASTORE && initInsns[1].slot == eventSlot
        initInsns[2].kind == 'insn' && initInsns[2].opcode == Opcodes.ACONST_NULL
        initInsns[3].kind == 'var' && initInsns[3].opcode == Opcodes.ASTORE && initInsns[3].slot == contextSlot
    }

    def "emitWithErrorBoundary registers a single Throwable catch and emits handler LDC equal to fqcnForHandler output"() {
        given:
        def recording = new InstructionRecordingMethodVisitor()
        def method = fixtureMethod("intParam")
        def body = { mv, eventSlot, contextSlot -> } as SyntheticMethodSkeleton.BodyEmitter

        when:
        SyntheticMethodSkeleton.emitWithErrorBoundary(
                recording, "com/example/Target", method, LogLevel.INFO, 4, 5, 6, [], body)

        then:
        recording.tryCatchBlocks.size() == 1
        recording.tryCatchBlocks[0].catchType == "java/lang/Throwable"
        recording.ldcValues.contains("com.example.Target#intParam(I)V")

        and:
        def handleCalls = recording.invokeStaticCalls.findAll {
            it.owner == "org/libprunus/core/log/runtime/StringBuilderWithContext" && it.name == "handleRenderFailure"
        }
        handleCalls.size() == 1
        handleCalls[0].descriptor ==
                "(Ljava/lang/String;Lorg/libprunus/core/log/runtime/StringBuilderWithContext;Ljava/lang/Throwable;)V"
    }

    def "emitWithErrorBoundary try block executes atLevel, ASTORE eventSlot, body sentinel, mark, logAndRelease, RETURN in order"() {
        given:
        def recording = new InstructionRecordingMethodVisitor()
        def method = fixtureMethod("intParam")
        def eventSlot = 4
        def contextSlot = 5
        def body = { mv, e, c -> mv.visitInsn(Opcodes.NOP) } as SyntheticMethodSkeleton.BodyEmitter

        when:
        SyntheticMethodSkeleton.emitWithErrorBoundary(
                recording, "com/example/Target", method, LogLevel.INFO, eventSlot, contextSlot, 6, [], body)

        then:
        def atLevelIdx = recording.timeline.findIndexOf {
            it.kind == 'method' && it.opcode == Opcodes.INVOKEINTERFACE && it.name == "atInfo"
        }
        def astoreEventAfterAtLevel = recording.timeline.findIndexOf(atLevelIdx + 1) {
            it.kind == 'var' && it.opcode == Opcodes.ASTORE && it.slot == eventSlot
        }
        def sentinelNopIdx = recording.timeline.findIndexOf(astoreEventAfterAtLevel + 1) {
            it.kind == 'insn' && it.opcode == Opcodes.NOP
        }
        def isTruncatedIdx = recording.timeline.findIndexOf(sentinelNopIdx + 1) {
            it.kind == 'method' && it.opcode == Opcodes.INVOKEVIRTUAL && it.name == "isTruncated"
        }
        def markIdx = recording.timeline.findIndexOf(isTruncatedIdx + 1) {
            it.kind == 'method' && it.opcode == Opcodes.INVOKEVIRTUAL && it.name == "markRenderTruncation"
        }
        def logAndReleaseIdx = recording.timeline.findIndexOf(markIdx + 1) {
            it.kind == 'method' && it.opcode == Opcodes.INVOKEVIRTUAL && it.name == "logAndRelease"
        }
        def normalReturnIdx = recording.timeline.findIndexOf(logAndReleaseIdx + 1) {
            it.kind == 'insn' && it.opcode == Opcodes.RETURN
        }

        atLevelIdx >= 0
        astoreEventAfterAtLevel > atLevelIdx
        sentinelNopIdx > astoreEventAfterAtLevel
        isTruncatedIdx > sentinelNopIdx
        markIdx > isTruncatedIdx
        logAndReleaseIdx > markIdx
        normalReturnIdx > logAndReleaseIdx
    }

    def "emitWithErrorBoundary catch handler emits ASTORE exceptionSlot, LDC fqcn, ALOAD contextSlot, ALOAD exceptionSlot, INVOKESTATIC handleRenderFailure, RETURN in order"() {
        given:
        def recording = new InstructionRecordingMethodVisitor()
        def method = fixtureMethod("intParam")
        def contextSlot = 5
        def exceptionSlot = 6
        def body = { mv, e, c -> } as SyntheticMethodSkeleton.BodyEmitter

        when:
        SyntheticMethodSkeleton.emitWithErrorBoundary(
                recording, "com/example/Target", method, LogLevel.INFO, 4, contextSlot, exceptionSlot, [], body)

        then:
        def handlerLabel = recording.tryCatchBlocks[0].handlerLabel
        handlerLabel != null
        def handlerLabelIdx = recording.timeline.findIndexOf {
            it.kind == 'label' && handlerLabel.is(it.labelRef)
        }
        def astoreExceptionIdx = recording.timeline.findIndexOf(handlerLabelIdx + 1) {
            it.kind == 'var' && it.opcode == Opcodes.ASTORE && it.slot == exceptionSlot
        }
        def ldcFqcnIdx = recording.timeline.findIndexOf(astoreExceptionIdx + 1) {
            it.kind == 'ldc' && it.value == "com.example.Target#intParam(I)V"
        }
        def aloadContextIdx = recording.timeline.findIndexOf(ldcFqcnIdx + 1) {
            it.kind == 'var' && it.opcode == Opcodes.ALOAD && it.slot == contextSlot
        }
        def aloadExceptionIdx = recording.timeline.findIndexOf(aloadContextIdx + 1) {
            it.kind == 'var' && it.opcode == Opcodes.ALOAD && it.slot == exceptionSlot
        }
        def handleStaticIdx = recording.timeline.findIndexOf(aloadExceptionIdx + 1) {
            it.kind == 'method' && it.opcode == Opcodes.INVOKESTATIC && it.name == "handleRenderFailure"
        }
        def catchReturnIdx = recording.timeline.findIndexOf(handleStaticIdx + 1) {
            it.kind == 'insn' && it.opcode == Opcodes.RETURN
        }

        handlerLabelIdx >= 0
        astoreExceptionIdx > handlerLabelIdx
        ldcFqcnIdx > astoreExceptionIdx
        aloadContextIdx > ldcFqcnIdx
        aloadExceptionIdx > aloadContextIdx
        handleStaticIdx > aloadExceptionIdx
        catchReturnIdx > handleStaticIdx
    }

    def "emitWithErrorBoundary registers try-catch labels spanning prologue end to RETURN with handler placed after tryEnd"() {
        given:
        def recording = new InstructionRecordingMethodVisitor()
        def method = fixtureMethod("intParam")
        def body = { mv, e, c -> } as SyntheticMethodSkeleton.BodyEmitter

        when:
        SyntheticMethodSkeleton.emitWithErrorBoundary(
                recording, "com/example/Target", method, LogLevel.INFO, 4, 5, 6, [], body)

        then:
        recording.tryCatchBlocks.size() == 1
        def block = recording.tryCatchBlocks[0]
        block.startLabel != null
        block.endLabel != null
        block.handlerLabel != null

        def tryStartIdx = recording.timeline.findIndexOf { it.kind == 'label' && block.startLabel.is(it.labelRef) }
        def tryEndIdx = recording.timeline.findIndexOf { it.kind == 'label' && block.endLabel.is(it.labelRef) }
        def handlerIdx = recording.timeline.findIndexOf { it.kind == 'label' && block.handlerLabel.is(it.labelRef) }
        def logAndReleaseIdx = recording.timeline.findIndexOf {
            it.kind == 'method' && it.opcode == Opcodes.INVOKEVIRTUAL && it.name == "logAndRelease"
        }

        // prologue is 4 entries (insn/var/insn/var); tryStart must come after them
        tryStartIdx >= 4
        // logAndRelease lies inside the try range
        logAndReleaseIdx > tryStartIdx
        tryEndIdx > logAndReleaseIdx
        // handler is registered after tryEnd
        handlerIdx > tryEndIdx
    }

    def "emitWithErrorBoundary skips enrich invocation when fieldExtractors is empty"() {
        given:
        def recording = new InstructionRecordingMethodVisitor()
        def method = fixtureMethod("intParam")
        def body = { mv, e, c -> } as SyntheticMethodSkeleton.BodyEmitter

        when:
        SyntheticMethodSkeleton.emitWithErrorBoundary(
                recording, "com/example/Target", method, LogLevel.INFO, 4, 5, 6, [], body)

        then:
        recording.invokeStaticCalls.findAll { it.name == '$lp$enrich' }.isEmpty()

        and:
        def atLevelCalls = recording.invokeInterfaceCalls.findAll { it.name == "atInfo" }
        atLevelCalls.size() == 1
    }

    def "emitWithErrorBoundary invokes synthetic enrich method when fieldExtractors is non-empty"() {
        given:
        def recording = new InstructionRecordingMethodVisitor()
        def method = fixtureMethod("intParam")
        def eventSlot = 4
        def body = { mv, e, c -> } as SyntheticMethodSkeleton.BodyEmitter
        def extractors = [new FieldExtractorRef("f", "com/example/Target", "getF", "()Ljava/lang/String;", false)]

        when:
        SyntheticMethodSkeleton.emitWithErrorBoundary(
                recording, "com/example/Target", method, LogLevel.INFO, eventSlot, 5, 6, extractors, body)

        then:
        def enrichCalls = recording.invokeStaticCalls.findAll {
            it.owner == "com/example/Target" && it.name == '$lp$enrich'
        }
        enrichCalls.size() == 1
        enrichCalls[0].descriptor == "(Lorg/slf4j/spi/LoggingEventBuilder;)Lorg/slf4j/spi/LoggingEventBuilder;"

        and:
        // enrich consumes ALOAD eventSlot, returns a new builder ASTORE'd back to the same slot
        def enrichInvokeIdx = recording.timeline.findIndexOf {
            it.kind == 'method' && it.opcode == Opcodes.INVOKESTATIC && it.name == '$lp$enrich'
        }
        def aloadBeforeEnrichIdx = recording.timeline.take(enrichInvokeIdx).findLastIndexOf {
            it.kind == 'var' && it.opcode == Opcodes.ALOAD && it.slot == eventSlot
        }
        def astoreAfterEnrichIdx = recording.timeline.findIndexOf(enrichInvokeIdx + 1) {
            it.kind == 'var' && it.opcode == Opcodes.ASTORE && it.slot == eventSlot
        }
        enrichInvokeIdx >= 0
        aloadBeforeEnrichIdx >= 0
        astoreAfterEnrichIdx > enrichInvokeIdx

        and:
        recording.invokeInterfaceCalls.findAll { it.name == "atInfo" }.size() == 1
    }

    def "emitWithErrorBoundary via enter and exit emitter facades registers a single Throwable catch routing to handleRenderFailure"() {
        given:
        def cw = new ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/Target", null, "java/lang/Object", null)
        def capture = new TryCatchAndMethodCallCapturingClassVisitor(Opcodes.ASM9, cw)
        def method = fixtureMethod(fixtureName)
        def ctx = new MethodLogContext("Fixture", fixtureName, ctxEnter, ctxExit)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, defaultMethodPlan(method, ctxEnter, ctxExit), ctx, "")

        when:
        emitter(capture, "test/Target", request, [])

        then:
        capture.catchTypes.size() == 1
        capture.catchTypes[0] == "java/lang/Throwable"
        capture.invokedMethods.any {
            it == "org/libprunus/core/log/runtime/StringBuilderWithContext.handleRenderFailure"
        }

        where:
        emitter                                    | fixtureName       | ctxEnter      | ctxExit
        SyntheticEnterEmitter.&emit                | "intParam"        | LogLevel.INFO | LogLevel.OFF
        SyntheticExitEmitter.&emit                 | "staticIntReturn" | LogLevel.OFF  | LogLevel.INFO
    }

    def "emitWithErrorBoundary via enter and exit emitter facades LDCs handler fqcn including method descriptor"() {
        given:
        def ldcValues = []
        def cv = new LdcCapturingClassVisitor(ldcValues)
        def method = fixtureMethod(fixtureName)
        def ctx = new MethodLogContext("Fixture", fixtureName, ctxEnter, ctxExit)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, defaultMethodPlan(method, ctxEnter, ctxExit), ctx, "")

        when:
        emitter(cv, "test/Fixture", request, [])

        then:
        ldcValues.contains(expectedLdc)

        where:
        emitter                                    | fixtureName       | ctxEnter      | ctxExit       | expectedLdc
        SyntheticEnterEmitter.&emit                | "intParam"        | LogLevel.INFO | LogLevel.OFF  | "test.Fixture#intParam(I)V"
        SyntheticExitEmitter.&emit                 | "staticIntReturn" | LogLevel.OFF  | LogLevel.INFO | "test.Fixture#staticIntReturn(I)I"
    }

    def "emitWithErrorBoundary via enter and exit emitter facades ASTOREs eventSlot before invoking enrich"() {
        given:
        def cw = new ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/Target", null, "java/lang/Object", null)
        def capture = new EventSlotEnrichOrderCapturingClassVisitor(Opcodes.ASM9, cw)
        def method = fixtureMethod(fixtureName)
        def ctx = new MethodLogContext("Fixture", fixtureName, ctxEnter, ctxExit)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, defaultMethodPlan(method, ctxEnter, ctxExit), ctx, "")
        def extractors = [new FieldExtractorRef("myField", "test/Config", "getMyField", "()Ljava/lang/String;", false)]

        when:
        emitter(capture, "test/Target", request, extractors)

        then:
        capture.enrichInvokeIndex >= 0
        capture.astoreEvents.any { it.counter < capture.enrichInvokeIndex && it.varIndex == expectedEventSlot }

        where:
        emitter                                    | fixtureName       | ctxEnter      | ctxExit       | expectedEventSlot
        SyntheticEnterEmitter.&emit                | "intParam"        | LogLevel.INFO | LogLevel.OFF  | 2
        SyntheticExitEmitter.&emit                 | "staticIntReturn" | LogLevel.OFF  | LogLevel.INFO | 2
    }

    def "emitWithErrorBoundary via enter and exit emitter facades guards isTruncated and markRenderTruncation before logAndRelease"() {
        given:
        def captured = []
        def cv = new TruncationOrderCapturingClassVisitor(captured)
        def method = fixtureMethod(fixtureName)
        def ctx = new MethodLogContext("Fixture", fixtureName, ctxEnter, ctxExit)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, defaultMethodPlan(method, ctxEnter, ctxExit), ctx, "")

        when:
        emitter(cv, "test/Fixture", request, [])

        then:
        def isTruncatedIdx = captured.findIndexOf {
            it[0] == 'method' && it[3] == 'isTruncated' &&
                    it[2] == "org/libprunus/core/log/runtime/StringBuilderWithContext"
        }
        def ifeqIdx = -1
        for (int i = isTruncatedIdx + 1; i < captured.size(); i++) {
            if (captured[i][0] == 'jump' && captured[i][1] == Opcodes.IFEQ) {
                ifeqIdx = i
                break
            }
        }
        def markIdx = captured.findIndexOf {
            it[0] == 'method' &&
                    it[1] == Opcodes.INVOKEVIRTUAL &&
                    it[2] == "org/libprunus/core/log/runtime/StringBuilderWithContext" &&
                    it[3] == 'markRenderTruncation'
        }
        def logAndReleaseIdx = captured.findIndexOf {
            it[0] == 'method' && it[3] == 'logAndRelease'
        }

        isTruncatedIdx >= 0
        ifeqIdx > isTruncatedIdx
        markIdx > ifeqIdx
        logAndReleaseIdx > markIdx

        where:
        emitter                                    | fixtureName       | ctxEnter      | ctxExit
        SyntheticEnterEmitter.&emit                | "intParam"        | LogLevel.INFO | LogLevel.OFF
        SyntheticExitEmitter.&emit                 | "staticIntReturn" | LogLevel.OFF  | LogLevel.INFO
    }

    private static class InvokeRecord {
        int opcode
        String owner
        String name
        String descriptor
    }

    private static class TryCatchRecord {
        String catchType
        Label startLabel
        Label endLabel
        Label handlerLabel
    }

    private static class TimelineEntry {
        String kind
        int opcode = -1
        Integer slot = null
        String name = null
        Object value = null
        Label labelRef = null
    }

    private static class InstructionRecordingMethodVisitor extends MethodVisitor {
        List<Integer> aloadVars = []
        List<Integer> astoreVars = []
        List<Object> ldcValues = []
        List<InvokeRecord> invokeInterfaceCalls = []
        List<InvokeRecord> invokeStaticCalls = []
        List<InvokeRecord> invokeVirtualCalls = []
        List<TryCatchRecord> tryCatchBlocks = []
        List<TimelineEntry> timeline = []

        InstructionRecordingMethodVisitor() { super(Opcodes.ASM9) }

        @Override
        void visitInsn(int opcode) {
            timeline << new TimelineEntry(kind: 'insn', opcode: opcode)
        }

        @Override
        void visitVarInsn(int opcode, int varIndex) {
            if (opcode == Opcodes.ALOAD) aloadVars << varIndex
            if (opcode == Opcodes.ASTORE) astoreVars << varIndex
            timeline << new TimelineEntry(kind: 'var', opcode: opcode, slot: varIndex)
        }

        @Override
        void visitLdcInsn(Object value) {
            ldcValues << value
            timeline << new TimelineEntry(kind: 'ldc', value: value)
        }

        @Override
        void visitJumpInsn(int opcode, Label label) {
            timeline << new TimelineEntry(kind: 'jump', opcode: opcode, labelRef: label)
        }

        @Override
        void visitLabel(Label label) {
            timeline << new TimelineEntry(kind: 'label', labelRef: label)
        }

        @Override
        void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            def rec = new InvokeRecord(opcode: opcode, owner: owner, name: name, descriptor: descriptor)
            if (opcode == Opcodes.INVOKEINTERFACE) invokeInterfaceCalls << rec
            if (opcode == Opcodes.INVOKESTATIC) invokeStaticCalls << rec
            if (opcode == Opcodes.INVOKEVIRTUAL) invokeVirtualCalls << rec
            timeline << new TimelineEntry(kind: 'method', opcode: opcode, name: name)
        }

        @Override
        void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            tryCatchBlocks << new TryCatchRecord(
                    catchType: type, startLabel: start, endLabel: end, handlerLabel: handler)
        }
    }

    private static class TryCatchAndMethodCallCapturingClassVisitor extends ClassVisitor {
        final List<String> catchTypes = []
        final List<String> invokedMethods = []

        TryCatchAndMethodCallCapturingClassVisitor(int api, ClassVisitor delegate) { super(api, delegate) }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            def mv = super.visitMethod(access, name, descriptor, signature, exceptions)
            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                    catchTypes << type
                    super.visitTryCatchBlock(start, end, handler, type)
                }

                @Override
                void visitMethodInsn(int opcode, String owner, String mName, String mDescriptor, boolean isInterface) {
                    invokedMethods << ("$owner.$mName" as String)
                    super.visitMethodInsn(opcode, owner, mName, mDescriptor, isInterface)
                }
            }
        }
    }

    private static class LdcCapturingClassVisitor extends ClassVisitor {
        private final List sink

        LdcCapturingClassVisitor(List sink) {
            super(Opcodes.ASM9)
            this.sink = sink
        }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                void visitLdcInsn(Object value) { if (value instanceof String) sink << value }
            }
        }
    }

    private static class EventSlotEnrichOrderCapturingClassVisitor extends ClassVisitor {
        final List<AstoreEvent> astoreEvents = []
        int enrichInvokeIndex = -1
        private int counter = 0

        EventSlotEnrichOrderCapturingClassVisitor(int api, ClassVisitor delegate) { super(api, delegate) }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            def mv = super.visitMethod(access, name, descriptor, signature, exceptions)
            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                void visitVarInsn(int opcode, int varIndex) {
                    if (opcode == Opcodes.ASTORE) {
                        astoreEvents << new AstoreEvent(counter, varIndex)
                    }
                    counter++
                    super.visitVarInsn(opcode, varIndex)
                }

                @Override
                void visitMethodInsn(int opcode, String owner, String mName, String mDescriptor, boolean isInterface) {
                    if (opcode == Opcodes.INVOKESTATIC && mName == '$lp$enrich') {
                        enrichInvokeIndex = counter
                    }
                    counter++
                    super.visitMethodInsn(opcode, owner, mName, mDescriptor, isInterface)
                }

                @Override
                void visitInsn(int opcode) { counter++; super.visitInsn(opcode) }

                @Override
                void visitIntInsn(int opcode, int operand) { counter++; super.visitIntInsn(opcode, operand) }

                @Override
                void visitLdcInsn(Object value) { counter++; super.visitLdcInsn(value) }

                @Override
                void visitJumpInsn(int opcode, Label label) { counter++; super.visitJumpInsn(opcode, label) }
            }
        }
    }

    private static class AstoreEvent {
        final int counter
        final int varIndex

        AstoreEvent(int counter, int varIndex) {
            this.counter = counter
            this.varIndex = varIndex
        }
    }

    private static class TruncationOrderCapturingClassVisitor extends ClassVisitor {
        private final List captured

        TruncationOrderCapturingClassVisitor(List captured) {
            super(Opcodes.ASM9)
            this.captured = captured
        }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            new MethodVisitor(Opcodes.ASM9) {
                @Override
                void visitMethodInsn(int opcode, String owner, String mName, String mDescriptor, boolean isInterface) {
                    captured << ['method', opcode, owner, mName, mDescriptor]
                }

                @Override
                void visitJumpInsn(int opcode, Label label) {
                    captured << ['jump', opcode]
                }
            }
        }
    }

    @SuppressWarnings("unused")
    static class Fixture {
        void voidNoArgs() {}

        void intParam(int x) {}

        static int staticIntReturn(int x) { return x }
    }
}
