package org.libprunus.core.plugin.aot.log

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.ClassWriter
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.pool.TypePool
import org.libprunus.core.config.CoreRuntimeConfig
import org.libprunus.core.log.runtime.AbstractLogConfig
import org.libprunus.core.log.runtime.LogRuntime
import org.libprunus.core.log.runtime.LogRuntimeConfig
import org.libprunus.core.plugin.aot.AotCompileContext
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectChainLeafDto
import org.libprunus.core.plugin.aot.log.fixture.inspect.InspectRegistry
import org.libprunus.core.plugin.aot.log.fixture.pojo.ArrayItemDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.BigStringDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.ClassIgnoredDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.CustomToStringDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.DirectFieldDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.ItemDtoRegistry
import org.libprunus.core.plugin.aot.log.fixture.pojo.ListItemDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.MapItemDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.MaskIgnoreDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.MixedDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.NestInnerDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.NestOuterDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.NoneDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.ObjectArrayItemDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.ObjectItemDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.SimpleItemDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.ThreeMaskedDto
import org.libprunus.core.plugin.aot.log.fixture.pojo.TwoDto
import spock.lang.Specification

class AotPojoTransformerIntegrationSpec extends Specification {

    def setupSpec() {
        // LogRuntime.initializeBinding is jvm-singleton; prior spec in the same gradle worker may have initialized it first
        try {
            LogRuntime.initializeBinding(new AbstractLogConfig() {
                @Override int getMaxMessageLength() { return 20 }
                @Override boolean isWhitelisted(Class<?> type) { return false }
            })
        } catch (IllegalStateException ignored) {}
        LogRuntime.linkToDataPlane(new java.util.concurrent.atomic.AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(true))))
    }

    def setup() {
        LogRuntime.@boundMaxMessageLength = 20
        LogRuntime.linkToDataPlane(new java.util.concurrent.atomic.AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(true))))
    }

    def "toString on POJO with two masked fields renders both fields as masked via single LDC batch constant"() {
        given:
        def bytes = transformClass(TwoDto)
        def loaded = loadFresh(bytes, TwoDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance.toString()

        then:
        noExceptionThrown()
        result == "TwoDto(a=***, b=***)"
    }

    def "toString on POJO with masked field followed by unmasked field renders masked then dynamic value"() {
        given:
        def bytes = transformClass(MixedDto)
        def loaded = loadFresh(bytes, MixedDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        instance.b = "v"

        when:
        def result = instance.toString()

        then:
        noExceptionThrown()
        result == "MixedDto(a=***, b=v)"
    }

    def "toString on transformed POJO replaces any pre-existing custom toString implementation with the AOT-generated version"() {
        given:
        // push budget so the AOT output is observable in full instead of collapsing to the truncation marker
        LogRuntime.@boundMaxMessageLength = 128
        def bytes = transformClass(CustomToStringDto)
        def loaded = loadFresh(bytes, CustomToStringDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        instance.name = "test"

        when:
        def result = instance.toString()

        then:
        result != "custom-sentinel"
        result.startsWith("CustomToStringDto(")

        cleanup:
        LogRuntime.@boundMaxMessageLength = 20
    }

    def "toString on ALL-only POJO whose accumulated fixed text exceeds budget is truncated via final flush budget guard"() {
        given:
        // ThreeMaskedDto fixed string is 35 chars > maxMessageLength 20
        def bytes = transformClass(ThreeMaskedDto)
        def loaded = loadFresh(bytes, ThreeMaskedDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance.toString()

        then:
        noExceptionThrown()
        result.length() <= 20
        result.contains("...[TRUNCATED]")
    }

    def "toString on transformed POJO renders any field type without VerifyError and triggers truncation marker"() {
        given:
        def bytes = transformClass(dtoClass)
        def loaded = loadFresh(bytes, dtoClass.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        populateField(instance)

        when:
        def result = instance.toString()

        then:
        noExceptionThrown()
        result.contains("...[TRUNCATED]")

        where:
        dtoClass            | populateField
        SimpleItemDto       | { it.label = "hello" }
        MapItemDto          | { it.attributes = [key: "value"] }
        ListItemDto         | { it.tags = ["a", "b"] }
        ArrayItemDto        | { it.counts = [1, 2, 3] as int[] }
        ObjectItemDto       | { it.payload = new Object() }
        ObjectArrayItemDto  | { it.items = ["x", "y"] as Object[] }
    }

    def "toString on transformed POJO truncates String field that exceeds maxMessageLength"() {
        given:
        def bytes = transformClass(SimpleItemDto)
        def loaded = loadFresh(bytes, SimpleItemDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        instance.label = "x" * 100

        when:
        def result = instance.toString()

        then:
        result.length() < 100
        !result.contains("x" * 100)
    }

    def "toString on transformed POJO returns fallback partial output when List field rendering triggers StackOverflowError"() {
        given:
        def bytes = transformClass(ListItemDto)
        def loaded = loadFresh(bytes, ListItemDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        instance.tags = new AbstractList<String>() {
            @Override String get(int index) { return "item" }
            @Override int size() { return 1 }
            @Override Iterator<String> iterator() {
                new Iterator<String>() {
                    boolean hasNext() { throw new StackOverflowError("deep") }
                    String next() { return null }
                }
            }
        }

        when:
        def result = instance.toString()

        then:
        noExceptionThrown()
        result.startsWith("ListItemDto(")
        result.contains("[SOE]")
        !result.contains("[TRUNCATED]")
    }

    def "toString on transformed POJO catches StackOverflowError thrown directly from render method and returns partial string"() {
        given:
        def rawBytes = transformClass(SimpleItemDto)
        def rewrittenBytes = rewriteRenderToThrow(rawBytes, "java/lang/StackOverflowError", "()V", null)
        def loaded = loadFresh(rewrittenBytes, SimpleItemDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance.toString()

        then:
        // render throws before appending anything; recoverToStringFallback returns the empty pool buffer contents
        noExceptionThrown()
        result != null
        result.isEmpty()
    }

    def "toString on transformed POJO catches RuntimeException thrown from render method and returns fallback partial string"() {
        given:
        def rawBytes = transformClass(SimpleItemDto)
        def rewrittenBytes = rewriteRenderToThrow(rawBytes, "java/lang/RuntimeException", "(Ljava/lang/String;)V", "render-failure")
        def loaded = loadFresh(rewrittenBytes, SimpleItemDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance.toString()

        then:
        // render throws before appending anything; recoverToStringFallback returns the empty pool buffer contents
        noExceptionThrown()
        result != null
        result.isEmpty()
    }

    def "toString on transformed POJO propagates non-SOE Error thrown from render method after releasing pool"() {
        given:
        def rawBytes = transformClass(SimpleItemDto)
        def rewrittenBytes = rewriteRenderToThrow(rawBytes, "java/lang/AssertionError", "(Ljava/lang/Object;)V", "render-assertion-failed")
        def loaded = loadFresh(rewrittenBytes, SimpleItemDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        instance.toString()

        then:
        thrown(AssertionError)
    }

    def "toString releases pool via recoverToStringFallback on every render failure even when invoked beyond pool depth"() {
        given:
        def rawBytes = transformClass(SimpleItemDto)
        def rewrittenBytes = rewriteRenderToThrow(rawBytes, "java/lang/RuntimeException", "(Ljava/lang/String;)V", "render-failure")
        def loaded = loadFresh(rewrittenBytes, SimpleItemDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        (1..20).each { instance.toString() }

        then:
        noExceptionThrown()
    }

    def "toString on POJO with unmasked field whose value overflows budget is truncated via in-loop budget guard"() {
        given:
        // prefix 'NoneDto(val=' is 12 chars + value 15 chars = 27 > budget 20
        def bytes = transformClass(NoneDto)
        def loaded = loadFresh(bytes, NoneDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        instance.val = "x" * 15

        when:
        def result = instance.toString()

        then:
        noExceptionThrown()
        result.contains("...[TRUNCATED]")
    }

    def "class-level @DoNotLog POJO transform emits render method and outputs empty body"() {
        def bytes = transformClass(ClassIgnoredDto)
        def loaded = loadFresh(bytes, ClassIgnoredDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        instance.secret = "sensitive-value"

        when:
        def result = instance.toString()

        then:
        result == "ClassIgnoredDto()"
        !result.contains("sensitive-value")
        loaded.declaredMethods.find { it.name == WeavingInternalNames.AOT_RENDER_METHOD } != null
    }

    def "class-level @DoNotLog POJO with field-level @Sensitive retains masked field as override"() {
        def bytes = transformClass(MaskIgnoreDto)
        def loaded = loadFresh(bytes, MaskIgnoreDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        instance.hidden = "should-not-appear"
        instance.v = "should-be-masked"

        when:
        def result = instance.toString()

        then:
        result == "MaskIgnoreDto(v=***)"
        !result.contains("should-not-appear")
        loaded.declaredMethods.find { it.name == WeavingInternalNames.AOT_RENDER_METHOD } != null
    }

    def "toString on transformed POJO reads field value via GETFIELD without invoking getter methods"() {
        given:
        // push budget so the GETFIELD-read value is observable in full instead of collapsing to the truncation marker
        LogRuntime.@boundMaxMessageLength = 128
        def bytes = transformClass(DirectFieldDto)
        def loaded = loadFresh(bytes, DirectFieldDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        when:
        def result = instance.toString()

        then:
        noExceptionThrown()
        result.startsWith("DirectFieldDto(value=")
        result.contains("field-value")

        cleanup:
        LogRuntime.@boundMaxMessageLength = 20
    }

    def "toString on transformed POJO with String field value vastly exceeding budget does not expand StringBuilder beyond maxMessageLength"() {
        given:
        // maxMessageLength=20, content=100_000 chars
        def bytes = transformClass(BigStringDto)
        def loaded = loadFresh(bytes, BigStringDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        instance.content = "x" * 100_000

        when:
        def result = instance.toString()

        then:
        result.length() <= 20
        result.contains("...[TRUNCATED]")
    }

    def "toString on POJO whose shadowed field declarers share the same simple name across packages still renders both values"() {
        given:
        // api.ProfileDto and db.ProfileDto both expose a public 'tag' field; the child ProfileDto extends api.ProfileDto so both declarations enter the same render plan
        LogRuntime.@boundMaxMessageLength = 256
        def bytes = transformClass(org.libprunus.core.plugin.aot.log.fixture.shadow.collision.db.ProfileDto)
        def loaded = loadFresh(bytes, org.libprunus.core.plugin.aot.log.fixture.shadow.collision.db.ProfileDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()
        instance.tag = "child-value"
        // fixture loaded by isolated classloader; groovy dynamic dispatch cannot statically resolve field setter across classloaders
        def apiTagField = org.libprunus.core.plugin.aot.log.fixture.shadow.collision.api.ProfileDto.class.getDeclaredField("tag")
        apiTagField.setAccessible(true)
        apiTagField.set(instance, "parent-value")

        when:
        def result = instance.toString()

        then:
        noExceptionThrown()
        result.contains("child-value")
        result.contains("parent-value")
        result.count("tag(ProfileDto)=") == 2

        cleanup:
        LogRuntime.@boundMaxMessageLength = 20
    }

    def "nested Loggable POJO field truncation produces exactly one TRUNCATED marker when both outer and inner render methods reach truncationLabel"() {
        given:
        // NestOuterDto prefix 'NestOuterDto(n=' is 15 chars, leaving 5 for inner; inner prefix 'NestInnerDto(a=' is also 15 chars and immediately exceeds the 5 remaining, triggering inner truncationLabel; outer truncationLabel also fires but forceAppendAuditMarker idempotency prevents double marker
        def innerBytes = transformClass(NestInnerDto)
        def outerBytes = transformClass(NestOuterDto)
        def innerLoaded = loadFresh(innerBytes, NestInnerDto.name)
        def outerLoaded = loadFresh(outerBytes, NestOuterDto.name)
        def innerInstance = innerLoaded.getDeclaredConstructor().newInstance()
        def outerInstance = outerLoaded.getDeclaredConstructor().newInstance()
        outerInstance.n = innerInstance

        when:
        def result = outerInstance.toString()

        then:
        noExceptionThrown()
        result.contains("...[TRUNCATED]")
        result.count("...[TRUNCATED]") == 1
    }

    def "toString on InspectChainLeafDto renders NONE fields with actual value, ALL fields as ***, and omits ignored fields at every level"() {
        given:
        // renders facet (vs O-003 InspectBehaviorIntegrationSpec which covers the inspect observability facet)
        // 3-level chain (Root -> Mid -> Leaf); distinct sentinel values set at every field slot
        // fixture loaded by isolated classloader; groovy dynamic dispatch cannot statically resolve field setter across classloaders
        LogRuntime.@boundMaxMessageLength = 2048
        def bytes = transformClass(InspectChainLeafDto, InspectRegistry)
        def loaded = loadFresh(bytes, InspectChainLeafDto.name)
        def instance = loaded.getDeclaredConstructor().newInstance()

        // leaf's own fields
        loaded.getField("leafPubNone").set(instance, "leaf-pub")
        loaded.getField("leafPubAll").set(instance, "leaf-all-secret")
        loaded.getField("leafPubIgnored").set(instance, "leaf-ignored-val")
        declaredField(loaded, "leafPrivNone").set(instance, "leaf-priv")
        declaredField(loaded, "leafPrivAll").set(instance, "leaf-priv-secret")

        // mid-level fields (1 level up, non-root)
        def mid = loaded.superclass
        mid.getField("midPubNone").set(instance, "mid-pub")
        mid.getField("midPubAll").set(instance, "mid-all-secret")
        mid.getField("midPubIgnored").set(instance, "mid-ignored-val")

        // root-level fields (2 levels up, non-root)
        def root = loaded.superclass.superclass
        root.getField("rootPubNone").set(instance, "root-pub")
        root.getField("rootPubAll").set(instance, "root-all-secret")
        root.getField("rootPubIgnored").set(instance, "root-ignored-val")
        declaredField(root, "rootProtNone").set(instance, "root-prot")
        declaredField(root, "rootProtAll").set(instance, "root-prot-secret")
        declaredField(root, "rootPrivNone").set(instance, "root-priv")
        declaredField(root, "rootPrivAll").set(instance, "root-priv-secret")

        when:
        def result = instance.toString()

        then:
        result == "InspectChainLeafDto(leafPubNone=leaf-pub, leafPubAll=***, leafPrivNone=leaf-priv, leafPrivAll=***, midPubNone=mid-pub, midPubAll=***, rootPubNone=root-pub, rootPubAll=***, rootProtNone=root-prot, rootProtAll=***)"

        cleanup:
        LogRuntime.@boundMaxMessageLength = 20
    }

    private static byte[] transformClass(Class<?> target, Class<?> registry = ItemDtoRegistry) {
        def locator = ClassFileLocator.ForClassLoader.of(target.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(target.name).resolve()
        def plugin = new AotLogByteBuddyPlugin(registry.name, locator, new AotCompileContext())
        def builder = new ByteBuddy().redefine(typeDesc, locator)
        plugin.apply(builder, typeDesc, locator).make().bytes
    }

    private static java.lang.reflect.Field declaredField(Class<?> cls, String name) {
        def f = cls.getDeclaredField(name)
        f.accessible = true
        f
    }

    private static byte[] rewriteRenderToThrow(byte[] bytes, String exceptionInternalName, String ctorDesc, String ctorArg) {
        def cr = new ClassReader(bytes)
        def cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if (name == WeavingInternalNames.AOT_RENDER_METHOD && desc == WeavingInternalNames.AOT_RENDER_DESCRIPTOR) {
                    def mv = super.visitMethod(access, name, desc, sig, ex)
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        void visitCode() {
                            mv.visitCode()
                            mv.visitTypeInsn(Opcodes.NEW, exceptionInternalName)
                            mv.visitInsn(Opcodes.DUP)
                            if (ctorArg != null) {
                                mv.visitLdcInsn(ctorArg)
                            }
                            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, exceptionInternalName, "<init>", ctorDesc, false)
                            mv.visitInsn(Opcodes.ATHROW)
                            mv.visitMaxs(0, 0)
                            mv.visitEnd()
                        }
                    }
                }
                super.visitMethod(access, name, desc, sig, ex)
            }
        }, 0)
        cw.toByteArray()
    }

    private static Class<?> loadFresh(byte[] bytes, String className) {
        def loader = new ClassLoader(AotPojoTransformerIntegrationSpec.classLoader) {
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name == className) {
                    return defineClass(name, bytes, 0, bytes.length)
                }
                throw new ClassNotFoundException(name)
            }

            Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name == className) {
                    def c = findLoadedClass(name)
                    if (c == null) c = findClass(name)
                    if (resolve) resolveClass(c)
                    return c
                }
                return super.loadClass(name, resolve)
            }
        }
        loader.loadClass(className)
    }
}
