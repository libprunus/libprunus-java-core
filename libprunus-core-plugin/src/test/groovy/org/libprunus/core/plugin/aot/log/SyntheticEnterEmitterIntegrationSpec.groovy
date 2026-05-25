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
import org.slf4j.LoggerFactory
import spock.lang.Specification

class SyntheticEnterEmitterIntegrationSpec extends Specification {

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

    def "emit produces bytecode that loads and executes a static-method synthetic enter without VerifyError"() {
        given:
        def className = "sample.synthetic.StaticMethodEnter${System.nanoTime()}"
        def internalName = className.replace('.', '/')
        def method = fixtureMethod("staticInts")
        def context = new AotMethodLoggingTransformer.MethodLogContext("Fixture", "staticInts", LogLevel.INFO, LogLevel.OFF)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method,
                methodPlan(method, LogLevel.INFO, LogLevel.OFF),
                context,
                "")

        def cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null)

        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        init.visitCode()
        init.visitVarInsn(Opcodes.ALOAD, 0)
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        init.visitInsn(Opcodes.RETURN)
        init.visitMaxs(0, 0)
        init.visitEnd()

        SyntheticEnterEmitter.emit(cw, internalName, request, [])
        cw.visitEnd()

        def bytes = cw.toByteArray()
        def loaded = loadClass(className, bytes)
        def logger = LoggerFactory.getLogger(className)
        def syntheticName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "staticInts"
        def syntheticMethod = loaded.getDeclaredMethod(syntheticName, org.slf4j.Logger, Integer.TYPE, Integer.TYPE)
        syntheticMethod.setAccessible(true)

        when:
        syntheticMethod.invoke(null, logger, 3, 7)

        then:
        noExceptionThrown()
    }

    def "emit produces bytecode that loads and executes a synthetic enter for a method with wide (long) parameters without VerifyError"() {
        given:
        def className = "sample.synthetic.StaticLongsEnter${System.nanoTime()}"
        def internalName = className.replace('.', '/')
        def method = fixtureMethod("staticLongs")
        def context = new AotMethodLoggingTransformer.MethodLogContext("Fixture", "staticLongs", LogLevel.INFO, LogLevel.OFF)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method,
                methodPlan(method, LogLevel.INFO, LogLevel.OFF),
                context,
                "")

        def cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null)
        SyntheticEnterEmitter.emit(cw, internalName, request, [])
        cw.visitEnd()

        def loaded = loadClass(className, cw.toByteArray())
        def logger = LoggerFactory.getLogger(className)
        def syntheticName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "staticLongs"
        def syntheticMethod = loaded.getDeclaredMethod(syntheticName, org.slf4j.Logger, Long.TYPE, Long.TYPE)
        syntheticMethod.setAccessible(true)

        when:
        syntheticMethod.invoke(null, logger, 42L, 99L)

        then:
        noExceptionThrown()
    }

    def "emit chained with emitEnrichMethod produces bytecode that loads and executes the synthetic enter without VerifyError"() {
        given:
        def className = "sample.synthetic.EnterEnrich${System.nanoTime()}"
        def internalName = className.replace('.', '/')
        def method = fixtureMethod("staticInts")
        def context = new AotMethodLoggingTransformer.MethodLogContext("Fixture", "staticInts", LogLevel.INFO, LogLevel.OFF)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method,
                methodPlan(method, LogLevel.INFO, LogLevel.OFF),
                context,
                "")
        def extractors = [new FieldExtractorRef("myField", internalName, "getMyField", "()Ljava/lang/String;", false)]

        def cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null)

        MethodVisitor getter = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getMyField", "()Ljava/lang/String;", null, null)
        getter.visitCode()
        getter.visitLdcInsn("v")
        getter.visitInsn(Opcodes.ARETURN)
        getter.visitMaxs(0, 0)
        getter.visitEnd()

        SyntheticMethodEmitter.emitEnrichMethod(cw, extractors)
        SyntheticEnterEmitter.emit(cw, internalName, request, extractors)
        cw.visitEnd()

        def loaded = loadClass(className, cw.toByteArray())
        def logger = LoggerFactory.getLogger(className)
        def syntheticName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "staticInts"
        def syntheticMethod = loaded.getDeclaredMethod(syntheticName, org.slf4j.Logger, Integer.TYPE, Integer.TYPE)
        syntheticMethod.setAccessible(true)

        when:
        syntheticMethod.invoke(null, logger, 3, 7)

        then:
        noExceptionThrown()
    }

    def "emit produces a synthetic enter that swallows a RuntimeException thrown by logAndRelease and does not propagate it to the caller"() {
        given:
        def className = "sample.synthetic.LogFailureEnter${System.nanoTime()}"
        def internalName = className.replace('.', '/')
        def method = fixtureMethod("staticInts")
        def context = new AotMethodLoggingTransformer.MethodLogContext(
                "Fixture", "staticInts", LogLevel.INFO, LogLevel.OFF)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, methodPlan(method, LogLevel.INFO, LogLevel.OFF), context, "")

        def cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null)
        SyntheticEnterEmitter.emit(cw, internalName, request, [])
        cw.visitEnd()

        def loaded = loadClass(className, cw.toByteArray())
        def syntheticName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "staticInts"
        def syntheticMethod = loaded.getDeclaredMethod(
                syntheticName, org.slf4j.Logger, Integer.TYPE, Integer.TYPE)
        syntheticMethod.setAccessible(true)

        def failingEventBuilder = Stub(org.slf4j.spi.LoggingEventBuilder) {
            log(_ as String) >> { throw new RuntimeException("backend logging failure") }
        }
        def failingLogger = Stub(org.slf4j.Logger) {
            atInfo() >> failingEventBuilder
        }

        when:
        syntheticMethod.invoke(null, failingLogger, 3, 7)

        then:
        noExceptionThrown()
    }

    def "emit produces a synthetic enter that propagates a non-SOE Error thrown by atInfo when contextSlot was never acquired"() {
        given:
        def className = "sample.synthetic.OomEarlyEnter${System.nanoTime()}"
        def internalName = className.replace('.', '/')
        def method = fixtureMethod("staticInts")
        def context = new AotMethodLoggingTransformer.MethodLogContext(
                "Fixture", "staticInts", LogLevel.INFO, LogLevel.OFF)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, methodPlan(method, LogLevel.INFO, LogLevel.OFF), context, "")

        def cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null)
        SyntheticEnterEmitter.emit(cw, internalName, request, [])
        cw.visitEnd()

        def loaded = loadClass(className, cw.toByteArray())
        def syntheticName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "staticInts"
        def syntheticMethod = loaded.getDeclaredMethod(
                syntheticName, org.slf4j.Logger, Integer.TYPE, Integer.TYPE)
        syntheticMethod.setAccessible(true)

        def oom = new OutOfMemoryError("heap exhausted")
        def failingLogger = Stub(org.slf4j.Logger) {
            atInfo() >> { throw oom }
        }

        when:
        syntheticMethod.invoke(null, failingLogger, 3, 7)

        then:
        def ex = thrown(java.lang.reflect.InvocationTargetException)
        ex.cause.is(oom)
    }

    def "emit embeds the original method descriptor in the handler ownerAndMethod LDC string of the synthetic enter"() {
        given:
        def internalName = "sample/synthetic/LdcDescriptorEnter"
        def method = fixtureMethod("staticInts")
        def ctx = new AotMethodLoggingTransformer.MethodLogContext(
                "Fixture", "staticInts", LogLevel.INFO, LogLevel.INFO)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, methodPlan(method, LogLevel.INFO, LogLevel.INFO), ctx, "")

        def cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null)
        SyntheticEnterEmitter.emit(cw, internalName, request, [])
        cw.visitEnd()
        def bytes = cw.toByteArray()

        def enterSyntheticName = WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "staticInts"

        when:
        def enterLdcStrings = collectLdcStrings(bytes, enterSyntheticName)

        then:
        enterLdcStrings.contains("sample.synthetic.LdcDescriptorEnter#staticInts(II)V")
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
        def loader = new ClassLoader(SyntheticEnterEmitterIntegrationSpec.classLoader) {
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
        static void staticLongs(long left, long right) {}
    }
}
