package org.libprunus.core.plugin.aot.log

import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import org.libprunus.core.plugin.aot.BindingIdSanitizer
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import spock.lang.Specification
import spock.lang.TempDir

class RuntimeBindingCallsiteGeneratorSpec extends Specification {

    @TempDir
    File tempDir

    def "callsiteClassName preserves binding id as middle package segment when sanitization is identity"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()

        expect:
        generator.callsiteClassName("b123") == "org.libprunus.aot.generated.b123.RuntimeBindingCallsite"
    }

    def "callsiteClassName transforms binding id with sanitized package segment and stable hash suffix when sanitization rewrites the id"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()

        expect:
        generator.callsiteClassName(bindingId) ==
                "org.libprunus.aot.generated.${expectedSegment}.RuntimeBindingCallsite"

        where:
        bindingId        || expectedSegment
        "my-core-module" || "my_core_module_74def24b02dedaadf5fbff0996b9ff95"
        "123_app"        || "_123_app_139a4112126e98dc277b15fd86c0c3c9"
        "default"        || "default__37a8eec1ce19687d132fe29051dca629"
    }

    def "callsiteClassName rejects blank binding ids with project specific message"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()

        when:
        generator.callsiteClassName(bindingId)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "bindingId must not be blank"

        where:
        bindingId << ["", "   "]
    }

    def "generate writes callsite class under binding id package"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()

        when:
        generator.generate("b123", "org.libprunus.aot.generated.b123.LogConfigBindingImpl", tempDir.toPath(), "25")

        then:
        new File(tempDir, "org/libprunus/aot/generated/b123/RuntimeBindingCallsite.class").exists()
    }

    def "generate writes exactly the bytes produced by generateBytes"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()
        def className = generator.callsiteClassName("b123")
        def expectedBytes = generator.generateBytes(
                className, "org.libprunus.aot.generated.b123.LogConfigBindingImpl", "21")

        when:
        generator.generate("b123", "org.libprunus.aot.generated.b123.LogConfigBindingImpl", tempDir.toPath(), "21")

        then:
        def written = new File(tempDir, "org/libprunus/aot/generated/b123/RuntimeBindingCallsite.class")
        written.bytes == expectedBytes
    }

    def "generate writes class file under sanitized binding id package segment"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()
        def sanitized = BindingIdSanitizer.sanitizeForPackageSegment("my-core-module")
        def selectedBindingClass = "org.libprunus.aot.generated.${sanitized}.LogConfigBindingImpl"

        when:
        generator.generate("my-core-module", selectedBindingClass, tempDir.toPath(), "21")

        then:
        new File(tempDir, "org/libprunus/aot/generated/${sanitized}/RuntimeBindingCallsite.class").exists()
        !new File(tempDir, "org/libprunus/aot/generated/my-core-module/RuntimeBindingCallsite.class").exists()
    }

    def "generate rejects null selectedBindingClass with field name in NPE"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()

        when:
        generator.generate("b123", null, tempDir.toPath(), "21")

        then:
        def ex = thrown(NullPointerException)
        ex.message == "selectedBindingClass"
        !new File(tempDir, "org/libprunus/aot/generated/b123/RuntimeBindingCallsite.class").exists()
    }

    def "generate rejects null outputDir with field name in NPE"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()

        when:
        generator.generate("b123", "org.libprunus.aot.generated.b123.LogConfigBindingImpl", null, "21")

        then:
        def ex = thrown(NullPointerException)
        ex.message == "outputDir"
    }

    def "generate rejects blank bindingId via sanitizer exception path"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()

        when:
        generator.generate("", "org.libprunus.aot.generated.b123.LogConfigBindingImpl", tempDir.toPath(), "21")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "bindingId must not be blank"
        tempDir.listFiles().length == 0
    }

    def "generateBytes emits bind method that instantiates selected binding class and invokes LogRuntime initializer"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()
        def className = "org.libprunus.aot.generated.b123.RuntimeBindingCallsite"

        when:
        def bytes = generator.generateBytes(className, "org.libprunus.aot.generated.b123.LogConfigBindingImpl", "21")
        def info = inspectBindMethod(bytes)

        then:
        info.newOwner == "org/libprunus/aot/generated/b123/LogConfigBindingImpl"
        info.invokedOwner == RuntimeBindingAbi.AOT_RUNTIME_INTERNAL_NAME
        info.invokedName == RuntimeBindingAbi.INITIALIZE_BINDING_METHOD
    }

    def "generateBytes bind sequence contains NEW DUP INVOKESPECIAL INVOKESTATIC RETURN in order and invokes selected binding constructor"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()
        def className = "org.libprunus.aot.generated.b123.RuntimeBindingCallsite"

        when:
        def bytes = generator.generateBytes(className, "org.libprunus.aot.generated.b123.LogConfigBindingImpl", "21")
        def info = inspectBindMethod(bytes)

        then:
        info.opcodes == [Opcodes.NEW, Opcodes.DUP, Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC, Opcodes.RETURN]
        info.invokeSpecialOwner == info.newOwner
        info.invokeSpecialName == "<init>"
        info.invokeSpecialDescriptor == "()V"
    }

    def "generateBytes emits public final class with private constructor and public static bind()V method"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()
        def className = "org.libprunus.aot.generated.b123.RuntimeBindingCallsite"

        when:
        def bytes = generator.generateBytes(className, "org.libprunus.aot.generated.b123.LogConfigBindingImpl", "21")
        def shape = inspectClassShape(bytes)

        then:
        shape.classAccess == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)
        shape.superName == "java/lang/Object"
        shape.methods.find { it.name == "<init>" }.access == Opcodes.ACC_PRIVATE
        shape.methods.find { it.name == "<init>" }.descriptor == "()V"
        shape.methods.find { it.name == "bind" }.access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
        shape.methods.find { it.name == "bind" }.descriptor == "()V"
        shape.methods.collect { it.name } as Set == ["<init>", "bind"] as Set
    }

    def "generateBytes encodes initializeBinding parameter descriptor from AbstractLogConfig FQCN"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()
        def className = "org.libprunus.aot.generated.b123.RuntimeBindingCallsite"

        when:
        def bytes = generator.generateBytes(className, "org.libprunus.aot.generated.b123.LogConfigBindingImpl", "21")
        def info = inspectBindMethod(bytes)

        then:
        info.invokedDescriptor == "(L" + PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN.replace('.', '/') + ";)V"
    }

    def "generateBytes encodes class file version derived from target compatibility"() {
        given:
        def generator = new RuntimeBindingCallsiteGenerator()
        def className = "org.libprunus.aot.generated.b.RuntimeBindingCallsite"

        expect:
        inspectBindMethod(generator.generateBytes(
                className,
                "org.libprunus.aot.generated.b.LogConfigBindingImpl",
                targetCompatibility)).version == expectedVersion

        where:
        targetCompatibility || expectedVersion
        "17"                || 61
        "21"                || 65
        "25"                || 69
    }

    private static Map<String, Object> inspectBindMethod(byte[] bytes) {
        def result = [opcodes: []]
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                result.version = version
            }

            @Override
            MethodVisitor visitMethod(
                    int access,
                    String methodName,
                    String methodDescriptor,
                    String signature,
                    String[] exceptions) {
                if (methodName == "bind") {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        void visitTypeInsn(int opcode, String type) {
                            result.opcodes << opcode
                            if (opcode == Opcodes.NEW) {
                                result.newOwner = type
                            }
                        }

                        @Override
                        void visitInsn(int opcode) {
                            result.opcodes << opcode
                        }

                        @Override
                        void visitMethodInsn(
                                int opcode,
                                String owner,
                                String invokedMethodName,
                                String invokedMethodDescriptor,
                                boolean isInterface) {
                            result.opcodes << opcode
                            if (opcode == Opcodes.INVOKESTATIC) {
                                result.invokedOwner = owner
                                result.invokedName = invokedMethodName
                                result.invokedDescriptor = invokedMethodDescriptor
                            } else if (opcode == Opcodes.INVOKESPECIAL) {
                                result.invokeSpecialOwner = owner
                                result.invokeSpecialName = invokedMethodName
                                result.invokeSpecialDescriptor = invokedMethodDescriptor
                            }
                        }
                    }
                }
                return null
            }
        }, 0)
        result
    }

    private static Map<String, Object> inspectClassShape(byte[] bytes) {
        def shape = [methods: []]
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                shape.classAccess = access
                shape.superName = superName
            }

            @Override
            MethodVisitor visitMethod(
                    int access,
                    String methodName,
                    String methodDescriptor,
                    String signature,
                    String[] exceptions) {
                shape.methods << [name: methodName, descriptor: methodDescriptor, access: access]
                return null
            }
        }, 0)
        shape
    }
}
