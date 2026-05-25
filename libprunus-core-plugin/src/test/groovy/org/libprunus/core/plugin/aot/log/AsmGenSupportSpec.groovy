package org.libprunus.core.plugin.aot.log

import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.Label
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import spock.lang.Specification

class AsmGenSupportSpec extends Specification {

    def "beginPublicFinalClass writes a public final class header with caller-supplied version and supertype"() {
        when:
        def cw = AsmGenSupport.beginPublicFinalClass(65, "com/example/Foo", "java/lang/Object")
        cw.visitEnd()
        def header = readClassHeader(cw.toByteArray())

        then:
        header.version == 65
        header.name == "com/example/Foo"
        header.superName == "java/lang/Object"
        header.access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)
        (header.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0
    }

    def "emitDefaultCtor writes <init>()V invoking supertype constructor with caller-supplied access modifier"() {
        given:
        def cw = AsmGenSupport.beginPublicFinalClass(65, "com/example/Bar", "java/lang/Object")
        AsmGenSupport.emitDefaultCtor(cw, "java/lang/Object", access)
        cw.visitEnd()
        def ctor = readCtor(cw.toByteArray())

        expect:
        ctor.found
        ctor.access == access
        ctor.descriptor == "()V"
        ctor.ops == [
                [kind: 'var', op: Opcodes.ALOAD, index: 0],
                [kind: 'method', op: Opcodes.INVOKESPECIAL, owner: "java/lang/Object", name: "<init>", desc: "()V"],
                [kind: 'insn', op: Opcodes.RETURN]
        ]
        ctor.unexpected == []

        where:
        access << [0, Opcodes.ACC_PUBLIC, Opcodes.ACC_PRIVATE, Opcodes.ACC_PROTECTED]
    }

    def "pushInt selects the smallest viable opcode tier across the four tier representatives"() {
        given:
        def events = []
        def mv = new MethodVisitor(Opcodes.ASM9) {
            void visitInsn(int op) { events << [kind: 'insn', op: op] }
            void visitIntInsn(int op, int operand) { events << [kind: 'intInsn', op: op, operand: operand] }
            void visitLdcInsn(Object cst) { events << [kind: 'ldc', value: cst] }
        }

        when:
        AsmGenSupport.pushInt(mv, value)

        then:
        events == [expectedEvent]

        where:
        value || expectedEvent
        0     || [kind: 'insn', op: Opcodes.ICONST_0]
        6     || [kind: 'intInsn', op: Opcodes.BIPUSH, operand: 6]
        128   || [kind: 'intInsn', op: Opcodes.SIPUSH, operand: 128]
        32768 || [kind: 'ldc', value: 32768]
    }

    private static Map readClassHeader(byte[] bytes) {
        def out = [:]
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                out.version = version
                out.access = access
                out.name = name
                out.superName = superName
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG)
        out
    }

    private static Map readCtor(byte[] bytes) {
        def result = [found: false, ops: [], unexpected: []]
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name != "<init>") return null
                result.found = true
                result.access = access
                result.descriptor = descriptor
                return new MethodVisitor(Opcodes.ASM9) {
                    void visitVarInsn(int op, int index) { result.ops << [kind: 'var', op: op, index: index] }
                    void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                        result.ops << [kind: 'method', op: op, owner: owner, name: n, desc: d]
                    }
                    void visitInsn(int op) { result.ops << [kind: 'insn', op: op] }
                    void visitFieldInsn(int op, String owner, String n, String d) { result.unexpected << [kind: 'field', op: op] }
                    void visitTypeInsn(int op, String type) { result.unexpected << [kind: 'type', op: op] }
                    void visitIntInsn(int op, int operand) { result.unexpected << [kind: 'intInsn', op: op] }
                    void visitLdcInsn(Object cst) { result.unexpected << [kind: 'ldc', value: cst] }
                    void visitJumpInsn(int op, Label label) { result.unexpected << [kind: 'jump', op: op] }
                    void visitInvokeDynamicInsn(String n, String d, Object handle, Object... bsmArgs) { result.unexpected << [kind: 'indy', name: n] }
                }
            }
        }, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG)
        result
    }
}
