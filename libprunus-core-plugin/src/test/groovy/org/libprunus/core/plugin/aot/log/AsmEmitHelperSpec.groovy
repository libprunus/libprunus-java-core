package org.libprunus.core.plugin.aot.log

import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import spock.lang.Specification

class AsmEmitHelperSpec extends Specification {

    def "private constructor blocks reflective instantiation"() {
        when:
        new AsmEmitHelper()

        then:
        thrown(UnsupportedOperationException)
    }

    def "appendString emits ALOAD contextSlot, LDC value, INVOKEVIRTUAL StringBuilderWithContext append, POP"() {
        given:
        def mv = new RecordingMethodVisitor()

        when:
        AsmEmitHelper.appendString(mv, 3, "foo")

        then:
        mv.events == [
                [kind: 'var', op: Opcodes.ALOAD, slot: 3],
                [kind: 'ldc', value: "foo"],
                [kind: 'method', op: Opcodes.INVOKEVIRTUAL, owner: AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME, name: "append", desc: AsmDescriptors.CONTEXT_APPEND_TEXT_DESCRIPTOR, itf: false],
                [kind: 'insn', op: Opcodes.POP]
        ]
    }

    def "appendString uses caller-supplied contextSlot for ALOAD slot index"() {
        given:
        def mv = new RecordingMethodVisitor()

        when:
        AsmEmitHelper.appendString(mv, contextSlot, "x")

        then:
        mv.events[0] == [kind: 'var', op: Opcodes.ALOAD, slot: contextSlot]

        where:
        contextSlot << [0, 1, 5, 255]
    }

    def "appendString passes caller-supplied value to LDC instruction unchanged"() {
        given:
        def mv = new RecordingMethodVisitor()

        when:
        AsmEmitHelper.appendString(mv, 1, value)

        then:
        mv.events[1] == [kind: 'ldc', value: value]
        mv.events[1].value.is(value)

        where:
        value << ["", "ASCII", "non-ascii-zhongwen", "x" * 1024]
    }

    def "markRenderTruncation emits ALOAD contextSlot then INVOKEVIRTUAL StringBuilderWithContext markRenderTruncation with no POP"() {
        given:
        def mv = new RecordingMethodVisitor()

        when:
        AsmEmitHelper.markRenderTruncation(mv, 4)

        then:
        mv.events == [
                [kind: 'var', op: Opcodes.ALOAD, slot: 4],
                [kind: 'method', op: Opcodes.INVOKEVIRTUAL, owner: AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME, name: "markRenderTruncation", desc: AsmDescriptors.CONTEXT_MARK_RENDER_TRUNCATION_DESCRIPTOR, itf: false]
        ]
    }

    def "markRenderTruncation uses caller-supplied contextSlot for ALOAD slot index"() {
        given:
        def mv = new RecordingMethodVisitor()

        when:
        AsmEmitHelper.markRenderTruncation(mv, contextSlot)

        then:
        mv.events[0] == [kind: 'var', op: Opcodes.ALOAD, slot: contextSlot]

        where:
        contextSlot << [0, 1, 5, 255]
    }

    def "isTruncated emits ALOAD contextSlot then INVOKEVIRTUAL StringBuilderWithContext isTruncated leaving boolean on stack without POP"() {
        given:
        def mv = new RecordingMethodVisitor()

        when:
        AsmEmitHelper.isTruncated(mv, 5)

        then:
        mv.events == [
                [kind: 'var', op: Opcodes.ALOAD, slot: 5],
                [kind: 'method', op: Opcodes.INVOKEVIRTUAL, owner: AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME, name: "isTruncated", desc: AsmDescriptors.CONTEXT_IS_TRUNCATED_DESCRIPTOR, itf: false]
        ]
    }

    def "isTruncated uses caller-supplied contextSlot for ALOAD slot index"() {
        given:
        def mv = new RecordingMethodVisitor()

        when:
        AsmEmitHelper.isTruncated(mv, contextSlot)

        then:
        mv.events[0] == [kind: 'var', op: Opcodes.ALOAD, slot: contextSlot]

        where:
        contextSlot << [0, 1, 5, 255]
    }

    private static class RecordingMethodVisitor extends MethodVisitor {

        final List<Map> events = []

        RecordingMethodVisitor() {
            super(Opcodes.ASM9)
        }

        @Override
        void visitVarInsn(int op, int slot) {
            events << [kind: 'var', op: op, slot: slot]
        }

        @Override
        void visitLdcInsn(Object value) {
            events << [kind: 'ldc', value: value]
        }

        @Override
        void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            events << [kind: 'method', op: op, owner: owner, name: name, desc: desc, itf: itf]
        }

        @Override
        void visitInsn(int op) {
            events << [kind: 'insn', op: op]
        }
    }
}
