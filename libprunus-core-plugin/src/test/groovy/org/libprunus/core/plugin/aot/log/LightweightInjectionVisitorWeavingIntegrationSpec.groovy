package org.libprunus.core.plugin.aot.log

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.Handle
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.pool.TypePool
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.log.annotation.MethodLoggingProfile
import org.libprunus.core.plugin.aot.AotCompileContext
import spock.lang.Specification

class LightweightInjectionVisitorWeavingIntegrationSpec extends Specification {

    def "woven bytecode uses INVOKESTATIC LogRuntime.isEnabled and no INVOKEDYNAMIC isEnabled"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(DemoService.classLoader)
        def plugin = new AotLogByteBuddyPlugin(MethodLoggingRegistry.name, locator, new AotCompileContext())
        def typeDesc = TypePool.Default.of(locator).describe(DemoService.name).resolve()

        when:
        def builder = new ByteBuddy().redefine(typeDesc, locator)
        def result = plugin.apply(builder, typeDesc, locator)
        byte[] bytecode = result.make().bytes

        then:
        def staticIsEnabled = collectStaticCalls(bytecode)
        staticIsEnabled.any {
            it.owner == "org/libprunus/core/log/runtime/LogRuntime" &&
                    it.name == "isEnabled" &&
                    it.desc == "()Z"
        }

        and:
        def invokeDynamics = collectInvokeDynamics(bytecode)
        !invokeDynamics.any { it.name == "isEnabled" && it.desc == "()Z" }
    }

    @LogRegistry
    @MethodLoggingProfile(includePackages = ["org.libprunus.core.plugin.aot.log"], includeClassSuffixes = ["Service"])
    static class MethodLoggingRegistry {}

    private static List<Map> collectInvokeDynamics(byte[] bytecode) {
        def found = []
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... bsmArgs) {
                        found << [name: n, desc: d, bsm: bsm]
                    }
                }
            }
        }, 0)
        found
    }

    private static List<Map> collectStaticCalls(byte[] bytecode) {
        def found = []
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitMethodInsn(int opcode, String owner, String n, String d, boolean iface) {
                        if (opcode == Opcodes.INVOKESTATIC) {
                            found << [owner: owner, name: n, desc: d]
                        }
                    }
                }
            }
        }, 0)
        found
    }

    static class DemoService {
        public String process(String input) {
            return "result:" + input
        }
    }
}
