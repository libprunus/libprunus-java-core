package org.libprunus.core.plugin.aot.log

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.ClassWriter
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.matcher.ElementMatchers
import org.libprunus.core.log.runtime.LogLevel
import spock.lang.Specification

class SyntheticExitEmitterIntegrationSpec extends Specification {

    private static final TypeDescription FIXTURE_TYPE = TypeDescription.ForLoadedType.of(Fixture)

    private static MethodDescription fixtureMethod(String name) {
        FIXTURE_TYPE.getDeclaredMethods()
                .filter(ElementMatchers.named(name))
                .getOnly()
    }

    private static ClassPlanAssembler.MethodPlan methodPlan(MethodDescription method, LogLevel enterLevel, LogLevel exitLevel) {
        def methodKey = new ClassPlanAssembler.MethodKey(
                method.getDeclaringType().asErasure().getInternalName(),
                method.getInternalName(),
                method.getDescriptor())
        new ClassPlanAssembler.MethodPlan(methodKey, [0L] as long[], [0L] as long[], false, false, enterLevel, exitLevel)
    }

    def "emit produces a synthetic exit that swallows a RuntimeException thrown by logAndRelease and does not propagate it to the caller"() {
        given:
        def className = "sample.synthetic.LogFailureExit${System.nanoTime()}"
        def internalName = className.replace('.', '/')
        def method = fixtureMethod("staticInts")
        def context = new AotMethodLoggingTransformer.MethodLogContext(
                "Fixture", "staticInts", LogLevel.OFF, LogLevel.INFO)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, methodPlan(method, LogLevel.OFF, LogLevel.INFO), context, "")

        def cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null)
        SyntheticExitEmitter.emit(cw, internalName, request, [])
        cw.visitEnd()

        def loaded = loadClass(className, cw.toByteArray())
        def syntheticName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "staticInts"
        def syntheticMethod = loaded.getDeclaredMethod(syntheticName, org.slf4j.Logger)
        syntheticMethod.setAccessible(true)

        def failingEventBuilder = Stub(org.slf4j.spi.LoggingEventBuilder) {
            log(_ as String) >> { throw new RuntimeException("backend logging failure") }
        }
        def failingLogger = Stub(org.slf4j.Logger) {
            atInfo() >> failingEventBuilder
        }

        when:
        syntheticMethod.invoke(null, failingLogger)

        then:
        noExceptionThrown()
    }

    def "emit produces a synthetic exit that propagates a non-SOE Error thrown by atInfo when contextSlot was never acquired"() {
        given:
        def className = "sample.synthetic.OomEarlyExit${System.nanoTime()}"
        def internalName = className.replace('.', '/')
        def method = fixtureMethod("staticInts")
        def context = new AotMethodLoggingTransformer.MethodLogContext(
                "Fixture", "staticInts", LogLevel.OFF, LogLevel.INFO)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, methodPlan(method, LogLevel.OFF, LogLevel.INFO), context, "")

        def cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null)
        SyntheticExitEmitter.emit(cw, internalName, request, [])
        cw.visitEnd()

        def loaded = loadClass(className, cw.toByteArray())
        def syntheticName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "staticInts"
        def syntheticMethod = loaded.getDeclaredMethod(syntheticName, org.slf4j.Logger)
        syntheticMethod.setAccessible(true)

        def oom = new OutOfMemoryError("heap exhausted")
        def failingLogger = Stub(org.slf4j.Logger) {
            atInfo() >> { throw oom }
        }

        when:
        syntheticMethod.invoke(null, failingLogger)

        then:
        def ex = thrown(java.lang.reflect.InvocationTargetException)
        ex.cause.is(oom)
    }

    def "emit embeds the original method descriptor in the handler ownerAndMethod LDC string of the synthetic exit"() {
        given:
        def internalName = "sample/synthetic/LdcDescriptorExit"
        def method = fixtureMethod("staticInts")
        def ctx = new AotMethodLoggingTransformer.MethodLogContext(
                "Fixture", "staticInts", LogLevel.INFO, LogLevel.INFO)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, methodPlan(method, LogLevel.INFO, LogLevel.INFO), ctx, "")

        def cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null)
        SyntheticExitEmitter.emit(cw, internalName, request, [])
        cw.visitEnd()
        def bytes = cw.toByteArray()

        def exitSyntheticName = WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "staticInts"

        when:
        def exitLdcStrings = collectLdcStrings(bytes, exitSyntheticName)

        then:
        exitLdcStrings.contains("sample.synthetic.LdcDescriptorExit#staticInts(II)V")
    }

    private static List<String> collectLdcStrings(byte[] bytes, String targetMethod) {
        def constants = []
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name != targetMethod) return null
                return new MethodVisitor(Opcodes.ASM9) {
                    void visitLdcInsn(Object value) { if (value instanceof String) constants << value }
                }
            }
        }, 0)
        constants
    }

    private static Class<?> loadClass(String className, byte[] classBytes) {
        def loader = new ClassLoader(SyntheticExitEmitterIntegrationSpec.classLoader) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name == className) {
                    return defineClass(name, classBytes, 0, classBytes.length)
                }
                throw new ClassNotFoundException(name)
            }
        }
        loader.loadClass(className)
    }

    static class Fixture {
        static void staticInts(int left, int right) {}
    }
}
