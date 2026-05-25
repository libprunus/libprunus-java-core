package org.libprunus.core.plugin.aot.log

import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import spock.lang.Specification

class AsmGenSupportAlgorithmSpec extends Specification {

    def "pushInt routes #value to the smallest viable opcode tier"() {
        given:
        def events = []
        def mv = new MethodVisitor(Opcodes.ASM9) {
            void visitInsn(int op) { events << [kind: 'insn', op: op] }
            void visitIntInsn(int op, int operand) { events << [kind: 'intInsn', op: op, operand: operand] }
            void visitLdcInsn(Object cst) { events << [kind: 'ldc', value: cst] }
            void visitVarInsn(int op, int index) { events << [kind: 'unexpected-var', op: op] }
            void visitMethodInsn(int op, String owner, String n, String d, boolean itf) { events << [kind: 'unexpected-method', op: op] }
            void visitFieldInsn(int op, String owner, String n, String d) { events << [kind: 'unexpected-field', op: op] }
            void visitTypeInsn(int op, String type) { events << [kind: 'unexpected-type', op: op] }
        }

        when:
        AsmGenSupport.pushInt(mv, value)

        then:
        events == [expectedEvent]

        where:
        value                 || expectedEvent
        0                     || [kind: 'insn', op: Opcodes.ICONST_0]
        1                     || [kind: 'insn', op: Opcodes.ICONST_1]
        5                     || [kind: 'insn', op: Opcodes.ICONST_5]
        6                     || [kind: 'intInsn', op: Opcodes.BIPUSH, operand: 6]
        -1                    || [kind: 'intInsn', op: Opcodes.BIPUSH, operand: -1]
        Byte.MAX_VALUE as int || [kind: 'intInsn', op: Opcodes.BIPUSH, operand: Byte.MAX_VALUE as int]
        Byte.MIN_VALUE as int || [kind: 'intInsn', op: Opcodes.BIPUSH, operand: Byte.MIN_VALUE as int]
        128                   || [kind: 'intInsn', op: Opcodes.SIPUSH, operand: 128]
        -129                  || [kind: 'intInsn', op: Opcodes.SIPUSH, operand: -129]
        Short.MAX_VALUE as int || [kind: 'intInsn', op: Opcodes.SIPUSH, operand: Short.MAX_VALUE as int]
        Short.MIN_VALUE as int || [kind: 'intInsn', op: Opcodes.SIPUSH, operand: Short.MIN_VALUE as int]
        32768                 || [kind: 'ldc', value: 32768]
        -32769                || [kind: 'ldc', value: -32769]
        Integer.MAX_VALUE     || [kind: 'ldc', value: Integer.MAX_VALUE]
        Integer.MIN_VALUE     || [kind: 'ldc', value: Integer.MIN_VALUE]
    }
}
