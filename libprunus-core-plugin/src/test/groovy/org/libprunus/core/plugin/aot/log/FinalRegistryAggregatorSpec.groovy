package org.libprunus.core.plugin.aot.log

import java.util.concurrent.atomic.AtomicInteger
import net.bytebuddy.dynamic.ClassFileLocator
import org.libprunus.core.log.annotation.DirectToStringWhitelist
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.log.annotation.MaxMessageLength
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import org.libprunus.core.plugin.aot.log.RuntimeBindingAbi
import spock.lang.Specification
import spock.lang.TempDir

class FinalRegistryAggregatorSpec extends Specification {

    @TempDir
    File tempDir

    def "AggregatedRegistryResult returns immutable defensive copy of merged whitelist"() {
        given:
        def source = ["a", "b"] as ArrayList
        def result = new FinalRegistryAggregator.AggregatedRegistryResult(64, source)

        when:
        result.mergedWhitelist().add("c")

        then:
        thrown(UnsupportedOperationException)
        result.mergedWhitelist() == ["a", "b"]
        source == ["a", "b"]
    }

    def "aggregate merges core builtin whitelist with registry whitelist entries"() {
        when:
        def result = aggregateFor(ListWhitelistRegistry)

        then:
        result.mergedWhitelist().containsAll(RuntimeBindingAbi.CORE_BUILTIN_WHITELIST)
        result.mergedWhitelist().contains("java.util.List")
    }

    def "aggregate exposes both maxMessageLength and directToStringWhitelist sourced from registry annotations"() {
        when:
        def result = aggregateFor(LimitsRegistry)

        then:
        result.maxMessageLength() == 512
        result.mergedWhitelist().contains("java.lang.String")
    }

    def "aggregate returns whitelist in ascending lexical order"() {
        given:
        def cpDir = new File(tempDir, "cp")
        def whitelistPath = new File(cpDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        whitelistPath.text = "java.util.Map\njava.util.List\njava.util.Collection\n"

        when:
        def result = aggregateFor(DefaultRegistry, [cpDir])

        then:
        result.mergedWhitelist() == result.mergedWhitelist().toSorted()
    }

    def "aggregate propagates WhitelistClassNameValidator rejection unwrapped"() {
        given:
        def cpDir = new File(tempDir, "cp-invalid")
        def whitelistPath = new File(cpDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        whitelistPath.text = "int\n"

        when:
        aggregateFor(DefaultRegistry, [cpDir])

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Invalid whitelist class name: int"
        ex.cause == null
    }

    def "aggregate propagates cannot-be-resolved rejection when whitelist class is absent from classpath"() {
        given:
        def cpDir = new File(tempDir, "cp-missing")
        def whitelistPath = new File(cpDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        whitelistPath.text = "com.nonexistent.totally.missing.Fantasia\n"

        when:
        aggregateFor(DefaultRegistry, [cpDir])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("com.nonexistent.totally.missing.Fantasia")
        ex.cause == null
    }

    def "aggregate ignores non-existent classpath files gracefully"() {
        given:
        def nonExistentFile = new File("/this/does/not/exist/fake.jar")

        when:
        def result = aggregateFor(DefaultRegistry, [nonExistentFile])

        then:
        noExceptionThrown()
        result.mergedWhitelist().containsAll(RuntimeBindingAbi.CORE_BUILTIN_WHITELIST)
        result.mergedWhitelist().size() == RuntimeBindingAbi.CORE_BUILTIN_WHITELIST.size()
    }

    def "aggregate ignores classpath entry that is neither a directory nor a jar file"() {
        given:
        def txtFile = new File(tempDir, "something.txt")
        txtFile.text = "this is not a jar nor a directory"

        when:
        def result = aggregateFor(DefaultRegistry, [txtFile])

        then:
        noExceptionThrown()
        result.mergedWhitelist().containsAll(RuntimeBindingAbi.CORE_BUILTIN_WHITELIST)
        result.mergedWhitelist().size() == RuntimeBindingAbi.CORE_BUILTIN_WHITELIST.size()
    }

    def "aggregate reads whitelist from a JAR on the classpath"() {
        given:
        def jarFile = new File(tempDir, "lib.jar")
        jarFile.withOutputStream { os ->
            def jos = new java.util.jar.JarOutputStream(os)
            jos.putNextEntry(new java.util.jar.JarEntry(PrunusPluginConstants.WHITELIST_RESOURCE_PATH))
            jos.write("java.lang.String\n".bytes)
            jos.closeEntry()
            jos.close()
        }

        when:
        def result = aggregateFor(DefaultRegistry, [jarFile])

        then:
        noExceptionThrown()
        result.mergedWhitelist().contains("java.lang.String")
    }

    def "aggregate strips UTF-8 BOM from first line of JAR whitelist resource"() {
        given:
        def jarFile = new File(tempDir, "bom-whitelist.jar")
        jarFile.withOutputStream { os ->
            def jos = new java.util.jar.JarOutputStream(os)
            jos.putNextEntry(new java.util.jar.JarEntry(PrunusPluginConstants.WHITELIST_RESOURCE_PATH))
            jos.write("﻿java.lang.String\n".getBytes("UTF-8"))
            jos.closeEntry()
            jos.close()
        }

        when:
        def result = aggregateFor(DefaultRegistry, [jarFile])

        then:
        noExceptionThrown()
        result.mergedWhitelist().contains("java.lang.String")
    }

    def "aggregate accepts whitelist file from directory whose byte count exactly equals the maximum allowed limit"() {
        given:
        def whitelistPath = new File(tempDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        def content = new byte[1024 * 1024]
        Arrays.fill(content, (byte) '\n')
        whitelistPath.bytes = content

        when:
        def result = aggregateFor(DefaultRegistry, [tempDir])

        then:
        noExceptionThrown()
        result.mergedWhitelist().containsAll(RuntimeBindingAbi.CORE_BUILTIN_WHITELIST)
    }

    def "aggregate rejects whitelist file on directory classpath whose size exceeds MAX_WHITELIST_RESOURCE_BYTES"() {
        given:
        def cpDir = new File(tempDir, "oversize-dir")
        def whitelistPath = new File(cpDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        def content = new byte[1024 * 1024 + 1]
        Arrays.fill(content, (byte) '\n')
        whitelistPath.bytes = content

        when:
        aggregateFor(DefaultRegistry, [cpDir])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Whitelist resource is too large")
        ex.message.contains(String.valueOf(1024 * 1024))
        ex.cause == null
    }

    def "aggregate rejects oversized whitelist entry from JAR via pre-check and reports declared entry size"() {
        given:
        def jarFile = new File(tempDir, "oversize-whitelist.jar")
        jarFile.withOutputStream { os ->
            def jos = new java.util.jar.JarOutputStream(os)
            jos.putNextEntry(new java.util.jar.JarEntry(PrunusPluginConstants.WHITELIST_RESOURCE_PATH))
            jos.write(("x" * (1024 * 1024 + 128)).bytes)
            jos.closeEntry()
            jos.close()
        }

        when:
        aggregateFor(DefaultRegistry, [jarFile])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Whitelist resource is too large")
        ex.message.contains("!" + PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        !ex.message.contains("exceeded max bytes while reading")
        ex.cause == null
    }

    def "aggregate handles JAR without whitelist resource gracefully"() {
        given:
        def jarFile = new File(tempDir, "empty.jar")
        jarFile.withOutputStream { os ->
            def jos = new java.util.jar.JarOutputStream(os)
            jos.putNextEntry(new java.util.jar.JarEntry("META-INF/MANIFEST.MF"))
            jos.write("Manifest-Version: 1.0\n".bytes)
            jos.closeEntry()
            jos.close()
        }

        when:
        def result = aggregateFor(DefaultRegistry, [jarFile])

        then:
        noExceptionThrown()
        result.mergedWhitelist().containsAll(RuntimeBindingAbi.CORE_BUILTIN_WHITELIST)
    }

    def "aggregate ignores directory that contains no whitelist file"() {
        given:
        def emptyDir = new File(tempDir, "classes")
        emptyDir.mkdirs()

        when:
        def result = aggregateFor(DefaultRegistry, [emptyDir])

        then:
        noExceptionThrown()
        result.mergedWhitelist().containsAll(RuntimeBindingAbi.CORE_BUILTIN_WHITELIST)
    }

    def "aggregate yields identical whitelist when runtime classpath order changes but semantic entries are same"() {
        given:
        def dirA = new File(tempDir, "cpA")
        def dirB = new File(tempDir, "cpB")
        def wlA = new File(dirA, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        def wlB = new File(dirB, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        wlA.parentFile.mkdirs()
        wlB.parentFile.mkdirs()
        wlA.text = "java.util.Set\njava.util.Map\n"
        wlB.text = "java.util.Map\njava.util.Set\n"

        when:
        def resultAB = aggregateFor(ListWhitelistRegistry, [dirA, dirB])
        def resultBA = aggregateFor(ListWhitelistRegistry, [dirB, dirA])

        then:
        resultAB.mergedWhitelist() == resultBA.mergedWhitelist()
    }

    def "aggregate accepts pure Iterable runtime classpath that is not a Collection"() {
        given:
        def cpDir = new File(tempDir, "iterable-only")
        def whitelistPath = new File(cpDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        whitelistPath.text = "java.util.List\n"
        Iterable<File> nonCollectionClasspath = { -> [cpDir].iterator() } as Iterable<File>

        when:
        def result = aggregateFor(DefaultRegistry, nonCollectionClasspath)

        then:
        noExceptionThrown()
        result.mergedWhitelist().containsAll(RuntimeBindingAbi.CORE_BUILTIN_WHITELIST)
        result.mergedWhitelist().contains("java.util.List")
    }

    def "aggregate deduplicates overlapping whitelist entries within file and across files"() {
        given:
        def dirA = new File(tempDir, "moduleA-dup")
        def dirB = new File(tempDir, "moduleB-dup")
        def wlA = new File(dirA, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        def wlB = new File(dirB, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        wlA.parentFile.mkdirs()
        wlB.parentFile.mkdirs()
        wlA.text = "java.util.UUID\njava.util.UUID\njava.util.Map\n"
        wlB.text = "java.util.UUID\njava.util.Set\njava.util.Map\n"

        when:
        def merged = aggregateFor(DefaultRegistry, [dirA, dirB]).mergedWhitelist()

        then:
        merged.size() == merged.toSet().size()
        merged.containsAll(["java.util.UUID", "java.util.Map", "java.util.Set"])
    }

    def "aggregate fails fast with IllegalStateException when classpath contains corrupt or non-ZIP JAR"() {
        given:
        def fakeJar = new File(tempDir, "corrupt.jar")
        fakeJar.text = "this is not a zip file"

        when:
        aggregateFor(DefaultRegistry, [fakeJar])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.startsWith("Failed to open JAR file: ")
        ex.message.contains(fakeJar.absolutePath)
        ex.cause instanceof IOException
    }

    def "aggregate closes the composed parser locator on the happy path"() {
        given:
        def counter = new AtomicInteger()
        def realLocator = ClassFileLocator.ForClassLoader.of(DefaultRegistry.classLoader)
        def spyLocator = new CloseCountingClassFileLocator(realLocator, counter)
        def cpDir = new File(tempDir, "happy-close")
        def whitelistPath = new File(cpDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        whitelistPath.text = "java.lang.String\n"

        when:
        def result = new FinalRegistryAggregator().aggregate(DefaultRegistry.name, spyLocator, [cpDir])

        then:
        counter.get() == 1
        result != null
        result.mergedWhitelist().contains("java.lang.String")
    }

    def "aggregate closes the composed parser locator on the exceptional path"() {
        given:
        def counter = new AtomicInteger()
        def realLocator = ClassFileLocator.ForClassLoader.of(DefaultRegistry.classLoader)
        def spyLocator = new CloseCountingClassFileLocator(realLocator, counter)
        def cpDir = new File(tempDir, "exceptional-close")
        def whitelistPath = new File(cpDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        whitelistPath.text = "int\n"

        when:
        new FinalRegistryAggregator().aggregate(DefaultRegistry.name, spyLocator, [cpDir])

        then:
        thrown(IllegalStateException)
        counter.get() == 1
    }

    def "aggregate propagates registry-class missing-LogRegistry rejection unwrapped"() {
        when:
        aggregateFor(NoAnnotationRegistry)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.startsWith("AOT registry class must be annotated with @LogRegistry: ")
        ex.message.contains(NoAnnotationRegistry.name)
        ex.cause == null
    }

    def "aggregate propagates registry-class-not-found rejection when class name is absent from locator"() {
        given:
        def emptyLocator = ClassFileLocator.NoOp.INSTANCE
        def absentName = "com.totally.absent.NotARealClass"

        when:
        new FinalRegistryAggregator().aggregate(absentName, emptyLocator, [])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("AOT registry class not found")
        ex.message.contains(absentName)
    }

    private static FinalRegistryAggregator.AggregatedRegistryResult aggregateFor(
            Class<?> registryClass, Iterable<File> runtimeClasspath = []) {
        def locator = ClassFileLocator.ForClassLoader.of(registryClass.classLoader)
        new FinalRegistryAggregator().aggregate(registryClass.name, locator, runtimeClasspath)
    }

    private static final class CloseCountingClassFileLocator implements ClassFileLocator {
        private final ClassFileLocator delegate
        private final AtomicInteger counter

        CloseCountingClassFileLocator(ClassFileLocator delegate, AtomicInteger counter) {
            this.delegate = delegate
            this.counter = counter
        }

        @Override
        ClassFileLocator.Resolution locate(String name) throws IOException {
            return delegate.locate(name)
        }

        @Override
        void close() throws IOException {
            counter.incrementAndGet()
            delegate.close()
        }
    }

    @LogRegistry
    static class DefaultRegistry {}

    @LogRegistry
    @DirectToStringWhitelist([List])
    static class ListWhitelistRegistry {}

    @LogRegistry
    @MaxMessageLength(512)
    @DirectToStringWhitelist([String])
    static class LimitsRegistry {}

    static class NoAnnotationRegistry {}
}
