package org.libprunus.core.plugin.aot.log

import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.pool.TypePool
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.log.annotation.MethodLoggingProfile
import org.libprunus.core.log.annotation.ToStringProfile
import org.libprunus.core.plugin.aot.AotCompileContext
import spock.lang.Specification

class AotLogByteBuddyPluginSpec extends Specification {

    private static final String LOGGABLE_INTERFACE_INTERNAL = "org/libprunus/core/log/runtime/Loggable"
    private static final String LOG_RUNTIME_INTERNAL = "org/libprunus/core/log/runtime/LogRuntime"

    def "constructor stores compile context reference and caches shared type pool for the locator"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(FooService.classLoader)
        def ctx = new AotCompileContext()

        when:
        def plugin = new AotLogByteBuddyPlugin(MethodOnlyRegistry.name, locator, ctx)

        then:
        plugin.@compileContext.is(ctx)
        ctx.@typePoolsByLocator.containsKey(locator)
        plugin.@routeGraph.methodLoggingRules().size() == 1
        plugin.@routeGraph.toStringRules().isEmpty()
    }

    def "constructor surfaces IllegalStateException when registry class is missing @LogRegistry annotation"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(PlainClassWithoutLogRegistry.classLoader)
        def ctx = new AotCompileContext()

        when:
        new AotLogByteBuddyPlugin(PlainClassWithoutLogRegistry.name, locator, ctx)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("@LogRegistry")
        ex.message.contains(PlainClassWithoutLogRegistry.name)
        ctx.@matchedPluginMasks.isEmpty()
    }

    def "constructor surfaces IllegalStateException when registry class cannot be resolved by the class file locator"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(FooService.classLoader)
        def ctx = new AotCompileContext()
        def missingName = "org.libprunus.does.not.Exist"

        when:
        new AotLogByteBuddyPlugin(missingName, locator, ctx)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("AOT registry class not found")
        ex.message.contains(missingName)
        ctx.@matchedPluginMasks.isEmpty()
    }

    def "matches returns false for interface types via short-circuit before route graph lookup"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(getClass().classLoader)
        def plugin = new AotLogByteBuddyPlugin(MatchAllServiceRegistry.name, locator, new AotCompileContext())
        def interfaceDesc = TypeDescription.ForLoadedType.of(Runnable)

        expect:
        interfaceDesc.isInterface()
        plugin.matches(interfaceDesc) == false
    }

    def "matches returns false for enum types via short-circuit before route graph lookup"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(getClass().classLoader)
        def plugin = new AotLogByteBuddyPlugin(MatchAllServiceRegistry.name, locator, new AotCompileContext())
        def enumDesc = TypeDescription.ForLoadedType.of(java.lang.annotation.RetentionPolicy)

        expect:
        enumDesc.isEnum()
        plugin.matches(enumDesc) == false
    }

    def "matches returns false for annotation types via short-circuit before route graph lookup"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(getClass().classLoader)
        def plugin = new AotLogByteBuddyPlugin(MatchAllServiceRegistry.name, locator, new AotCompileContext())
        def annotationDesc = TypeDescription.ForLoadedType.of(LogRegistry)

        expect:
        annotationDesc.isAnnotation()
        plugin.matches(annotationDesc) == false
    }

    def "matches returns true for a plain class when the route graph deems it route-relevant"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(FooService.classLoader)
        def plugin = new AotLogByteBuddyPlugin(MatchAllServiceRegistry.name, locator, new AotCompileContext())
        def typeDesc = TypePool.Default.of(locator).describe(FooService.name).resolve()

        expect:
        plugin.matches(typeDesc) == true
    }

    def "matches returns false for a plain class when the route graph deems it not route-relevant"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(Unrelated.classLoader)
        def plugin = new AotLogByteBuddyPlugin(MatchAllServiceRegistry.name, locator, new AotCompileContext())
        def typeDesc = TypePool.Default.of(locator).describe(Unrelated.name).resolve()

        expect:
        plugin.matches(typeDesc) == false
    }

    def "apply chains the method logging transformer only when the method profile is eligible"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(FooService.classLoader)
        def plugin = new AotLogByteBuddyPlugin(MethodOnlyRegistry.name, locator, new AotCompileContext())
        def typeDesc = TypePool.Default.of(locator).describe(FooService.name).resolve()
        def builder = new ByteBuddy().redefine(typeDesc, locator)

        when:
        def transformed = plugin.apply(builder, typeDesc, locator)
        def bytes = transformed.make().bytes

        then:
        !transformed.is(builder)
        hasInvokeStaticTo(bytes, LOG_RUNTIME_INTERNAL, "isEnabled")
        !implementsInterface(bytes, LOGGABLE_INTERFACE_INTERNAL)
    }

    def "apply chains the pojo transformer and implements Loggable only when the tostring profile is eligible"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(BarDto.classLoader)
        def plugin = new AotLogByteBuddyPlugin(ToStringOnlyRegistry.name, locator, new AotCompileContext())
        def typeDesc = TypePool.Default.of(locator).describe(BarDto.name).resolve()
        def builder = new ByteBuddy().redefine(typeDesc, locator)

        when:
        def transformed = plugin.apply(builder, typeDesc, locator)
        def bytes = transformed.make().bytes

        then:
        !transformed.is(builder)
        implementsInterface(bytes, LOGGABLE_INTERFACE_INTERNAL)
        !hasInvokeStaticTo(bytes, LOG_RUNTIME_INTERNAL, "isEnabled")
    }

    def "apply chains both transformers when method and tostring profiles are both eligible"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(BothDto.classLoader)
        def plugin = new AotLogByteBuddyPlugin(BothProfilesRegistry.name, locator, new AotCompileContext())
        def typeDesc = TypePool.Default.of(locator).describe(BothDto.name).resolve()
        def builder = new ByteBuddy().redefine(typeDesc, locator)

        when:
        def transformed = plugin.apply(builder, typeDesc, locator)
        def bytes = transformed.make().bytes

        then:
        !transformed.is(builder)
        hasInvokeStaticTo(bytes, LOG_RUNTIME_INTERNAL, "isEnabled")
        implementsInterface(bytes, LOGGABLE_INTERFACE_INTERNAL)
    }

    def "apply returns the original builder unchanged when neither profile is eligible for the type"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(Unrelated.classLoader)
        def plugin = new AotLogByteBuddyPlugin(BothProfilesRegistry.name, locator, new AotCompileContext())
        def typeDesc = TypePool.Default.of(locator).describe(Unrelated.name).resolve()
        def builder = new ByteBuddy().redefine(typeDesc, locator)

        when:
        def transformed = plugin.apply(builder, typeDesc, locator)
        def bytes = transformed.make().bytes

        then:
        transformed.is(builder)
        !hasInvokeStaticTo(bytes, LOG_RUNTIME_INTERNAL, "isEnabled")
        !implementsInterface(bytes, LOGGABLE_INTERFACE_INTERNAL)
    }

    def "close clears matched plugin masks and shared type pools on the compile context"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(FooService.classLoader)
        def ctx = new AotCompileContext()
        def plugin = new AotLogByteBuddyPlugin(MethodOnlyRegistry.name, locator, ctx)
        ctx.computeMaskIfAbsent("seed.ClassName", { String n -> 7 })

        expect:
        ctx.@matchedPluginMasks.size() == 1
        ctx.@typePoolsByLocator.size() >= 1

        when:
        plugin.close()

        then:
        ctx.@matchedPluginMasks.isEmpty()
        ctx.@typePoolsByLocator.isEmpty()
    }

    def "close is idempotent and leaves both compile context maps empty when invoked twice"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(FooService.classLoader)
        def ctx = new AotCompileContext()
        def plugin = new AotLogByteBuddyPlugin(MethodOnlyRegistry.name, locator, ctx)
        plugin.close()

        when:
        plugin.close()

        then:
        noExceptionThrown()
        ctx.@matchedPluginMasks.isEmpty()
        ctx.@typePoolsByLocator.isEmpty()
    }

    private static boolean hasInvokeStaticTo(byte[] bytecode, String ownerInternalName, String methodName) {
        def found = [false]
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitMethodInsn(int opcode, String owner, String n, String d, boolean iface) {
                        if (opcode == Opcodes.INVOKESTATIC && owner == ownerInternalName && n == methodName) {
                            found[0] = true
                        }
                    }
                }
            }
        }, 0)
        found[0]
    }

    private static boolean implementsInterface(byte[] bytecode, String interfaceInternalName) {
        def found = [false]
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
                if (interfaces != null) {
                    for (String iface : interfaces) {
                        if (iface == interfaceInternalName) {
                            found[0] = true
                        }
                    }
                }
            }
        }, 0)
        found[0]
    }

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Service"])
    static class MethodOnlyRegistry {}

    @LogRegistry
    @ToStringProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Dto"])
    static class ToStringOnlyRegistry {}

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Dto"])
    @ToStringProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Dto"])
    static class BothProfilesRegistry {}

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Service", "Dto"])
    @ToStringProfile(
            includePackages = ["org.libprunus.core.plugin.aot.log"],
            includeClassSuffixes = ["Service", "Dto"])
    static class MatchAllServiceRegistry {}

    static class PlainClassWithoutLogRegistry {}

    static class FooService {
        public String greet(String name) {
            return "hello " + name
        }
    }

    static class BarDto {
        public String value
    }

    static class BothDto {
        public String label

        public String render(String suffix) {
            return label + suffix
        }
    }

    static class Unrelated {
        public String operate(String input) {
            return input + "!"
        }
    }
}
