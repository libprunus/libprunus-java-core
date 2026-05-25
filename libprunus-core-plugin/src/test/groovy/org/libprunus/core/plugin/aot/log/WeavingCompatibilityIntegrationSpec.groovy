package org.libprunus.core.plugin.aot.log

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.pool.TypePool
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.log.annotation.MethodLoggingProfile
import org.libprunus.core.log.annotation.ToStringProfile
import org.libprunus.core.plugin.aot.AotCompileContext
import spock.lang.Specification

class WeavingCompatibilityIntegrationSpec extends Specification {

    def "method advice synthetic enter renders reference parameters through StringBuilderWithContext render"() {
        given:
        def bytes = transform(MethodAdviceTargetService)
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "capture"

        when:
        def events = collectMethodEvents(bytes, enterMethodName)
        def virtualCalls = events.findAll { it.kind == "method" && it.opcode == Opcodes.INVOKEVIRTUAL }

        then:
        virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME &&
                    it.name == "render" &&
                    it.desc == AsmDescriptors.CONTEXT_APPEND_OBJECT_DESCRIPTOR
        }
        !virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME &&
                    it.name == "append" &&
                    it.desc == "(Ljava/lang/Object;)Z"
        }
    }

    def "method advice synthetic enter appends primitive int parameters through typed StringBuilderWithContext append"() {
        given:
        def bytes = transform(MethodAdviceTargetService)
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "capture"

        when:
        def events = collectMethodEvents(bytes, enterMethodName)
        def virtualCalls = events.findAll { it.kind == "method" && it.opcode == Opcodes.INVOKEVIRTUAL }
        def staticCalls = events.findAll { it.kind == "method" && it.opcode == Opcodes.INVOKESTATIC }

        then:
        virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME &&
                    it.name == "append" &&
                    it.desc == AsmDescriptors.contextAppendPrimitiveDescriptor(Type.INT_TYPE)
        }
        !staticCalls.any { it.owner == "java/lang/Integer" && it.name == "valueOf" }
    }

    def "method advice synthetic enter does not autobox primitive parameters before append"() {
        given:
        def bytes = transform(MethodAdviceTargetService)
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "capture"

        when:
        def events = collectMethodEvents(bytes, enterMethodName)
        def staticCalls = events.findAll { it.kind == "method" && it.opcode == Opcodes.INVOKESTATIC }

        then:
        !staticCalls.any { it.owner == "java/lang/Integer" && it.name == "valueOf" }
    }

    def "method advice synthetic enter pops every StringBuilderWithContext append return value"() {
        given:
        def bytes = transform(MethodAdviceTargetService)
        def enterMethodName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "capture"

        when:
        def events = collectMethodEvents(bytes, enterMethodName)
        def appendIndexes = findAppendIndexes(events)

        then:
        appendIndexes.size() >= 1
        everyAppendCallIsPopped(events)
    }

    def "pojo render method appends primitive int fields through typed StringBuilderWithContext append"() {
        given:
        def bytes = transform(ToStringTargetDto)

        when:
        def events = collectMethodEvents(bytes, WeavingInternalNames.AOT_RENDER_METHOD)
        def virtualCalls = events.findAll { it.kind == "method" && it.opcode == Opcodes.INVOKEVIRTUAL }
        def staticCalls = events.findAll { it.kind == "method" && it.opcode == Opcodes.INVOKESTATIC }

        then:
        virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME &&
                    it.name == "append" &&
                    it.desc == AsmDescriptors.contextAppendPrimitiveDescriptor(Type.INT_TYPE)
        }
        !staticCalls.any { it.owner == "java/lang/Integer" && it.name == "valueOf" }
    }

    def "pojo render method renders reference fields through StringBuilderWithContext render"() {
        given:
        def bytes = transform(ToStringTargetDto)

        when:
        def events = collectMethodEvents(bytes, WeavingInternalNames.AOT_RENDER_METHOD)
        def virtualCalls = events.findAll { it.kind == "method" && it.opcode == Opcodes.INVOKEVIRTUAL }

        then:
        virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME &&
                    it.name == "render" &&
                    it.desc == AsmDescriptors.CONTEXT_APPEND_OBJECT_DESCRIPTOR
        }
        !virtualCalls.any {
            it.owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME &&
                    it.name == "append" &&
                    it.desc == "(Ljava/lang/Object;)Z"
        }
    }

    def "pojo render method does not autobox primitive fields before append"() {
        given:
        def bytes = transform(ToStringTargetDto)

        when:
        def events = collectMethodEvents(bytes, WeavingInternalNames.AOT_RENDER_METHOD)
        def staticCalls = events.findAll { it.kind == "method" && it.opcode == Opcodes.INVOKESTATIC }

        then:
        !staticCalls.any { it.owner == "java/lang/Integer" && it.name == "valueOf" }
    }

    def "pojo render method pops every StringBuilderWithContext append return value"() {
        given:
        def bytes = transform(ToStringTargetDto)

        when:
        def events = collectMethodEvents(bytes, WeavingInternalNames.AOT_RENDER_METHOD)
        def appendIndexes = findAppendIndexes(events)

        then:
        appendIndexes.size() >= 1
        everyAppendCallIsPopped(events)
    }

    private static byte[] transform(Class<?> target) {
        def locator = ClassFileLocator.ForClassLoader.of(target.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(target.name).resolve()
        def plugin = new AotLogByteBuddyPlugin(WeavingCompatibilityRegistry.name, locator, new AotCompileContext())
        def builder = new ByteBuddy().redefine(typeDesc, locator)
        plugin.apply(builder, typeDesc, locator).make().bytes
    }

    private static List<Map<String, Object>> collectMethodEvents(byte[] bytecode, String methodName) {
        def events = []
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name != methodName) {
                    return null
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitMethodInsn(int opcode, String owner, String n, String d, boolean isInterface) {
                        events << [kind: "method", opcode: opcode, owner: owner, name: n, desc: d]
                    }

                    @Override
                    void visitInsn(int opcode) {
                        events << [kind: "insn", opcode: opcode]
                    }
                }
            }
        }, 0)
        events
    }

    private static List<Integer> findAppendIndexes(List<Map<String, Object>> events) {
        def appendIndexes = []
        for (int i = 0; i < events.size(); i++) {
            def event = events[i]
            if (event.kind == "method" && event.name == "append") {
                appendIndexes << i
            }
        }
        appendIndexes
    }

    private static boolean everyAppendCallIsPopped(List<Map<String, Object>> events) {
        def appendIndexes = findAppendIndexes(events)
        appendIndexes && appendIndexes.every { index ->
            events.size() > index + 1 && events[index + 1].kind == "insn" && events[index + 1].opcode == Opcodes.POP
        }
    }

    @LogRegistry
    @MethodLoggingProfile(includePackages = ["org.libprunus.core.plugin.aot.log"], includeClassSuffixes = ["Service"])
    @ToStringProfile(includePackages = ["org.libprunus.core.plugin.aot.log"], includeClassSuffixes = ["Dto"])
    static class WeavingCompatibilityRegistry {}

    static class MethodAdviceTargetService {
        String capture(List<String> tags, Map<String, Integer> attrs, int count) {
            return tags.size() + ":" + attrs.size() + ":" + count
        }
    }

    static class ToStringTargetDto {
        public int count
        public List<String> tags
        public Map<String, Integer> attrs
    }
}
