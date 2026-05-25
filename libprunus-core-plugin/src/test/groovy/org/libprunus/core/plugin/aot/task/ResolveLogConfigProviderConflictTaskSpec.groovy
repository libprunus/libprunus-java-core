package org.libprunus.core.plugin.aot.task

import java.io.IOException
import java.io.UncheckedIOException
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import spock.lang.Specification
import spock.lang.TempDir

class ResolveLogConfigProviderConflictTaskSpec extends Specification {

    @TempDir
    File tempDir

    static final String SPI_NAME = PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN
    static final String SPI_PATH = "${PrunusPluginConstants.SPI_SERVICES_DIR}/${SPI_NAME}"
    static final String DEFAULT_BINDING_NAME = BindingClassSelector.defaultBindingClassName("b123")

    def "runtimeClasspathCacheInputs returns empty list when runtime classpath is empty and explicit binding is empty"() {
        given:
        def task = newTask("cache-inputs-empty-classpath")
        task.bindingId.set("b123")
        task.explicitBindingClass.set("")

        when:
        def cacheInputs = task.runtimeClasspathCacheInputs

        then:
        noExceptionThrown()
        cacheInputs.isEmpty()
        cacheInputs == RuntimeClasspathInputSelector.selectResolveCacheInputs([] as Set, DEFAULT_BINDING_NAME)
    }

    def "runtimeClasspathCacheInputs uses default binding path when explicitBindingClass is unset"() {
        given:
        def dirWithDefaultBinding = createDirWithBinding("cache-inputs-default-unset-dir", DEFAULT_BINDING_NAME)
        def task = newTask("cache-inputs-default-unset")
        task.bindingId.set("b123")
        task.runtimeClasspath.from(dirWithDefaultBinding)

        when:
        def cacheInputs = task.runtimeClasspathCacheInputs

        then:
        cacheInputs.contains(dirWithDefaultBinding.canonicalFile)
        cacheInputs == RuntimeClasspathInputSelector.selectResolveCacheInputs(
                [dirWithDefaultBinding] as Set, DEFAULT_BINDING_NAME)
    }

    def "runtimeClasspathCacheInputs delegates to RuntimeClasspathInputSelector with the selected explicit binding class name"() {
        given:
        def selectedBinding = "org.example.CustomBinding"
        def bindingClassPath = selectedBinding.replace('.', '/') + ".class"

        def bindingDir = new File(tempDir, "cache-input-binding-dir")
        def bindingClassFile = new File(bindingDir, bindingClassPath)
        bindingClassFile.parentFile.mkdirs()
        bindingClassFile.bytes = new byte[0]

        def irrelevantDir = new File(tempDir, "cache-input-irrelevant-dir")
        new File(irrelevantDir, "unrelated/Other.class").with {
            parentFile.mkdirs()
            bytes = new byte[0]
        }

        def bindingJar = new File(tempDir, "cache-input-binding.jar")
        new JarOutputStream(new FileOutputStream(bindingJar)).withCloseable { jos ->
            jos.putNextEntry(new JarEntry(bindingClassPath))
            jos.write(new byte[0])
            jos.closeEntry()
        }

        def task = newTask("cache-inputs-binding-relevance")
        task.bindingId.set("b123")
        task.explicitBindingClass.set(selectedBinding)
        task.runtimeClasspath.from(bindingDir, irrelevantDir, bindingJar)

        when:
        def cacheInputs = task.runtimeClasspathCacheInputs

        then:
        cacheInputs == RuntimeClasspathInputSelector.selectResolveCacheInputs(
                [bindingDir, irrelevantDir, bindingJar] as Set, selectedBinding)
        cacheInputs.contains(bindingDir.canonicalFile)
        cacheInputs.contains(bindingJar.canonicalFile)
        !cacheInputs.contains(irrelevantDir.canonicalFile)
    }

    def "runtimeClasspathCacheInputs does not add a jar content filter on top of the selector"() {
        given:
        def selectedBinding = "org.example.CustomBinding"

        def emptyJar = new File(tempDir, "cache-input-empty.jar")
        new JarOutputStream(new FileOutputStream(emptyJar)).withCloseable { jos ->
            jos.putNextEntry(new JarEntry("sample/Other.class"))
            jos.write(new byte[0])
            jos.closeEntry()
        }

        def irrelevantDir = new File(tempDir, "cache-input-irrelevant-dir-2")
        new File(irrelevantDir, "unrelated/Other.class").with {
            parentFile.mkdirs()
            bytes = new byte[0]
        }

        def task = newTask("cache-inputs-all-jars-included")
        task.bindingId.set("b123")
        task.explicitBindingClass.set(selectedBinding)
        task.runtimeClasspath.from(emptyJar, irrelevantDir)

        when:
        def cacheInputs = task.runtimeClasspathCacheInputs

        then:
        cacheInputs == RuntimeClasspathInputSelector.selectResolveCacheInputs(
                [emptyJar, irrelevantDir] as Set, selectedBinding)
        cacheInputs.contains(emptyJar.canonicalFile)
        !cacheInputs.contains(irrelevantDir.canonicalFile)
    }

    def "runtimeClasspathCacheInputs returns the selector output for both default and explicit binding paths"() {
        given:
        def jarA = new File(tempDir, "sort-a.jar")
        new JarOutputStream(new FileOutputStream(jarA)).withCloseable { jos ->
            jos.putNextEntry(new JarEntry("sample/Other.class"))
            jos.write(new byte[0])
            jos.closeEntry()
        }
        def jarB = new File(tempDir, "sort-b.jar")
        new JarOutputStream(new FileOutputStream(jarB)).withCloseable { jos ->
            jos.putNextEntry(new JarEntry("sample/Other.class"))
            jos.write(new byte[0])
            jos.closeEntry()
        }
        def jarC = new File(tempDir, "sort-c.jar")
        new JarOutputStream(new FileOutputStream(jarC)).withCloseable { jos ->
            jos.putNextEntry(new JarEntry("sample/Other.class"))
            jos.write(new byte[0])
            jos.closeEntry()
        }

        def task = newTask("cache-inputs-sorted-${scenario}")
        task.bindingId.set("b123")
        task.explicitBindingClass.set(explicitBinding)
        task.runtimeClasspath.from(jarC, jarA, jarB)

        when:
        def cacheInputs = task.runtimeClasspathCacheInputs

        then:
        cacheInputs == [jarA.canonicalFile, jarB.canonicalFile, jarC.canonicalFile]
        cacheInputs.size() == 3

        where:
        scenario   | explicitBinding
        "default"  | ""
        "explicit" | "org.example.CustomBinding"
    }

    def "should fail fast in cache-input getter when explicit binding class is invalid"() {
        given:
        def task = newTask("cache-inputs-reserved-namespace")
        task.bindingId.set("b123")
        task.explicitBindingClass.set("java.lang.CustomBinding")

        when:
        task.runtimeClasspathCacheInputs

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("reserved package namespace")
        ex.message.contains("java.lang.CustomBinding")
    }

    def "resolve with default binding completes silently when classpath is empty"() {
        given:
        def emptyCpDir = new File(tempDir, "cp-empty").tap { mkdirs() }
        def task = newTask("resolve-default-empty")
        task.bindingId.set("b123")
        task.explicitBindingClass.set("")
        task.runtimeClasspath.from(emptyCpDir)

        when:
        task.resolve()

        then:
        noExceptionThrown()
        LogConfigProviderScanner.scan(
                [emptyCpDir] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, DEFAULT_BINDING_NAME))
                .providerSources()
                .isEmpty()
    }

    def "resolve with default binding does not fail when exactly one jar contributes the SPI descriptor"() {
        given:
        def onlyProvider = createJarWithSpi("only-provider.jar")
        def task = newTask("resolve-default-single-spi")
        task.bindingId.set("b123")
        task.explicitBindingClass.set("")
        task.runtimeClasspath.from(onlyProvider)

        when:
        task.resolve()

        then:
        noExceptionThrown()
        LogConfigProviderScanner.scan(
                [onlyProvider] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, DEFAULT_BINDING_NAME))
                .providerSources()
                .size() == 1
    }

    def "resolve with default binding does not fail when classpath has multiple SPI descriptor jars but no binding class"() {
        given:
        def provider1 = createJarWithSpi("provider1.jar")
        def provider2 = createJarWithSpi("provider2.jar")
        def task = newTask("resolve-default-multi-spi")
        task.bindingId.set("b123")
        task.explicitBindingClass.set("")
        task.runtimeClasspath.from(provider1, provider2)

        when:
        task.resolve()

        then:
        noExceptionThrown()
        def scanResult = LogConfigProviderScanner.scan(
                [provider1, provider2] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, DEFAULT_BINDING_NAME))
        scanResult.providerSources().size() == 2
        scanResult.classSources().isEmpty()
    }

    def "resolve with default binding completes when unique binding class jar is present without provider conflict"() {
        given:
        def dirWithDefaultBinding = createDirWithBinding("resolve-default-unique-dir", DEFAULT_BINDING_NAME)
        def task = newTask("resolve-default-unique")
        task.bindingId.set("b123")
        task.explicitBindingClass.set("")
        task.runtimeClasspath.from(dirWithDefaultBinding)

        when:
        task.resolve()

        then:
        noExceptionThrown()
        def scanResult = LogConfigProviderScanner.scan(
                [dirWithDefaultBinding] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, DEFAULT_BINDING_NAME))
        scanResult.classSources().size() == 1
        scanResult.providerSources().isEmpty()
    }

    def "resolve with default binding succeeds even when binding class is absent from classpath"() {
        given:
        def dirWithoutBinding = new File(tempDir, "resolve-default-missing-dir").tap { mkdirs() }
        new File(dirWithoutBinding, "unrelated/Other.class").with {
            parentFile.mkdirs()
            bytes = new byte[0]
        }
        def task = newTask("resolve-default-missing-binding")
        task.bindingId.set("b123")
        task.explicitBindingClass.set("")
        task.runtimeClasspath.from(dirWithoutBinding)

        when:
        task.resolve()

        then:
        noExceptionThrown()
        def scanResult = LogConfigProviderScanner.scan(
                [dirWithoutBinding] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, DEFAULT_BINDING_NAME))
        scanResult.classSources().isEmpty()
    }

    def "resolve fails when explicit binding class is not on classpath"() {
        given:
        def task = newTask("resolve-explicit-missing")
        task.bindingId.set("b123")
        task.explicitBindingClass.set("org.example.CustomBinding")
        task.runtimeClasspath.from([])

        when:
        task.resolve()

        then:
        def ex = thrown(GradleException)
        ex.message.startsWith("Binding class ")
        ex.message.contains("org.example.CustomBinding")
        ex.message.contains("not found in classpath")
    }

    def "resolve completes when explicit binding class is found uniquely regardless of surrounding whitespace"() {
        given:
        def dir = new File(tempDir, "cp-${scenario}")
        def classFile = new File(dir, "org/example/CustomBinding.class")
        classFile.parentFile.mkdirs()
        classFile.bytes = new byte[0]
        def task = newTask("resolve-explicit-unique-${scenario}")
        task.bindingId.set("b123")
        task.explicitBindingClass.set(explicitBinding)
        task.runtimeClasspath.from(dir)

        when:
        task.resolve()

        then:
        noExceptionThrown()

        where:
        scenario   | explicitBinding
        "exact"    | "org.example.CustomBinding"
        "trimmed"  | "  org.example.CustomBinding  "
    }

    def "resolve fails when binding class appears in multiple classpath entries"() {
        given:
        def bindingFqcn = explicitBinding.isEmpty() ? DEFAULT_BINDING_NAME : explicitBinding
        def dir1 = createDirWithBinding("duplicate-cp-1-${scenario}", bindingFqcn)
        def dir2 = createDirWithBinding("duplicate-cp-2-${scenario}", bindingFqcn)

        def task = newTask("resolve-duplicate-${scenario}")
        task.bindingId.set("b123")
        task.explicitBindingClass.set(explicitBinding)
        task.runtimeClasspath.from(dir1, dir2)

        when:
        task.resolve()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("found in multiple jars")
        ex.message.contains(bindingFqcn)
        ex.message.contains(dir1.absolutePath)
        ex.message.contains(dir2.absolutePath)

        where:
        scenario   | explicitBinding
        "default"  | ""
        "explicit" | "org.example.CustomBinding"
    }

    def "resolve propagates UncheckedIOException from selectResolveScanEntries when classpath contains a corrupted jar"() {
        given:
        def brokenJar = new File(tempDir, "broken.jar")
        brokenJar.bytes = [] as byte[]
        def task = newTask("resolve-broken-jar")
        task.bindingId.set("b123")
        task.explicitBindingClass.set("")
        task.runtimeClasspath.from(brokenJar)

        when:
        task.resolve()

        then:
        def ex = thrown(UncheckedIOException)
        ex.message.contains("Failed to inspect JAR entry")
        ex.message.contains(brokenJar.absolutePath)
        ex.cause instanceof IOException
    }

    // --- helper ---

    private ResolveLogConfigProviderConflictTask newTask(String projectName) {
        def project = ProjectBuilder.builder().withName(projectName).build()
        project.tasks.create(projectName, ResolveLogConfigProviderConflictTask)
    }

    private File createDirWithBinding(String dirName, String fqcn) {
        def dir = new File(tempDir, dirName)
        def classFile = new File(dir, fqcn.replace('.', '/') + ".class")
        classFile.parentFile.mkdirs()
        classFile.bytes = new byte[0]
        dir
    }

    private File createJarWithSpi(String name) {
        def jar = new File(tempDir, name)
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { jos ->
            jos.putNextEntry(new JarEntry(SPI_PATH))
            jos.write("com.example.Provider".bytes)
            jos.closeEntry()
        }
        jar
    }
}
