package org.libprunus.core.plugin.aot.log

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import net.bytebuddy.dynamic.ClassFileLocator
import org.libprunus.core.log.annotation.DirectToStringWhitelist
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import org.libprunus.core.plugin.aot.log.RuntimeBindingAbi
import spock.lang.Specification
import spock.lang.TempDir

class RegistryMetadataWriterSpec extends Specification {

    @TempDir
    Path tempDir

    def writer = new RegistryMetadataWriter()

    def "write writes registry whitelist into target resource file"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(ListRegistry.classLoader)

        when:
        writer.write(ListRegistry.name, locator, tempDir)

        then:
        def target = tempDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        Files.exists(target)
        Files.readAllLines(target, StandardCharsets.UTF_8) == ["java.util.List", "java.lang.String"]
    }

    def "write uses builtin whitelist when direct whitelist annotation is absent"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(DefaultRegistry.classLoader)

        when:
        writer.write(DefaultRegistry.name, locator, tempDir)

        then:
        def target = tempDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        Files.exists(target)
        def lines = Files.readAllLines(target, StandardCharsets.UTF_8)
        lines.containsAll(RuntimeBindingAbi.CORE_BUILTIN_WHITELIST)
        lines.size() == RuntimeBindingAbi.CORE_BUILTIN_WHITELIST.size()
    }

    def "write deduplicates repeated DirectToStringWhitelist entries and preserves first declaration order"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(DuplicateEntryRegistry.classLoader)

        when:
        writer.write(DuplicateEntryRegistry.name, locator, tempDir)

        then:
        def target = tempDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        Files.readAllLines(target, StandardCharsets.UTF_8) == ["java.util.List", "java.lang.String"]
    }

    def "write overwrites an existing whitelist file with new content"() {
        given:
        def target = tempDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        Files.createDirectories(target.parent)
        Files.write(target, "old.Class\n".getBytes(StandardCharsets.UTF_8))
        def locator = ClassFileLocator.ForClassLoader.of(ListRegistry.classLoader)

        when:
        writer.write(ListRegistry.name, locator, tempDir)

        then:
        def lines = Files.readAllLines(target, StandardCharsets.UTF_8)
        lines.contains("java.util.List")
        !lines.contains("old.Class")
    }

    def "write rethrows builder error as IllegalStateException when registry class lacks LogRegistry annotation"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(NoAnnotationRegistry.classLoader)

        when:
        writer.write(NoAnnotationRegistry.name, locator, tempDir)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("must be annotated with @LogRegistry")
        ex.message.contains(NoAnnotationRegistry.name)
        !Files.exists(tempDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH))
    }

    def "write rethrows builder error as IllegalStateException when registry class is not present on the locator"() {
        given:
        def locator = ClassFileLocator.NoOp.INSTANCE

        when:
        writer.write("com.totally.absent.NotARealClass", locator, tempDir)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("AOT registry class not found")
        ex.message.contains("com.totally.absent.NotARealClass")
        !Files.exists(tempDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH))
    }

    def "write rethrows builder error as IllegalStateException when registryClassName is null"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(ListRegistry.classLoader)

        when:
        writer.write(null, locator, tempDir)

        then:
        thrown(IllegalStateException)
        !Files.exists(tempDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH))
    }

    def "write rethrows builder error as IllegalStateException when classFileLocator is null"() {
        when:
        writer.write(ListRegistry.name, null, tempDir)

        then:
        thrown(IllegalStateException)
        !Files.exists(tempDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH))
    }

    def "write wraps IOException into IllegalStateException when output directory cannot be written"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(ListRegistry.classLoader)
        def outputDir = Files.createTempFile(tempDir, "blocked", "")

        when:
        writer.write(ListRegistry.name, locator, outputDir)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.startsWith("Failed to write whitelist file:")
        ex.cause instanceof IOException
        !Files.exists(outputDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH))
    }

    def "write propagates SecurityException when path access is denied by runtime policy"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(ListRegistry.classLoader)
        def restrictedOutputDir = Mock(Path)
        restrictedOutputDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH) >> {
            throw new SecurityException("write denied")
        }

        when:
        writer.write(ListRegistry.name, locator, restrictedOutputDir)

        then:
        def ex = thrown(SecurityException)
        ex.message == "write denied"
    }

    def "write does not close the supplied classFileLocator after successful execution"() {
        given:
        def delegate = ClassFileLocator.ForClassLoader.of(ListRegistry.classLoader)
        def closeCount = new AtomicInteger(0)
        def spyLocator = new CloseCountingClassFileLocator(delegate, closeCount)

        when:
        writer.write(ListRegistry.name, spyLocator, tempDir)

        then:
        closeCount.get() == 0
        Files.exists(tempDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH))
    }

    def "write uses LF-only line endings regardless of host OS to ensure byte-for-byte hash determinism"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(ListRegistry.classLoader)

        when:
        writer.write(ListRegistry.name, locator, tempDir)

        then:
        def content = new String(Files.readAllBytes(tempDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH)), StandardCharsets.UTF_8)
        !content.contains('\r')
        content.count("\n") == 2
    }

    def "write writes an empty whitelist file when registry declares an empty DirectToStringWhitelist"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(EmptyWhitelistRegistry.classLoader)

        when:
        writer.write(EmptyWhitelistRegistry.name, locator, tempDir)

        then:
        def target = tempDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        Files.exists(target)
        Files.size(target) == 0L
    }

    def "write appends a trailing LF after the only whitelist entry"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(SingleEntryRegistry.classLoader)

        when:
        writer.write(SingleEntryRegistry.name, locator, tempDir)

        then:
        def target = tempDir.resolve(PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        Files.readAllBytes(target) == "java.lang.String\n".getBytes(StandardCharsets.UTF_8)
    }

    @LogRegistry
    static class DefaultRegistry {}

    @LogRegistry
    @DirectToStringWhitelist([List, String])
    static class ListRegistry {}

    @LogRegistry
    @DirectToStringWhitelist([])
    static class EmptyWhitelistRegistry {}

    @LogRegistry
    @DirectToStringWhitelist([String])
    static class SingleEntryRegistry {}

    @LogRegistry
    @DirectToStringWhitelist([List, List, String])
    static class DuplicateEntryRegistry {}

    static class NoAnnotationRegistry {}

    static class CloseCountingClassFileLocator implements ClassFileLocator {

        private final ClassFileLocator delegate
        private final AtomicInteger closeCount

        CloseCountingClassFileLocator(ClassFileLocator delegate, AtomicInteger closeCount) {
            this.delegate = delegate
            this.closeCount = closeCount
        }

        @Override
        ClassFileLocator.Resolution locate(String name) throws IOException {
            return delegate.locate(name)
        }

        @Override
        void close() throws IOException {
            closeCount.incrementAndGet()
            delegate.close()
        }
    }
}
