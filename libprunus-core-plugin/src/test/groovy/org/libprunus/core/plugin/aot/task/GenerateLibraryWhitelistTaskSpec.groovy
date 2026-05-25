package org.libprunus.core.plugin.aot.task

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir

class GenerateLibraryWhitelistTaskSpec extends Specification {

    private static final String TEST_REGISTRY_CLASS = 'org.libprunus.core.plugin.aot.task.fixture.WhitelistOnlyRegistry'
    private static final String LOG_REGISTRY_ANNOTATION_CLASS = 'org.libprunus.core.log.annotation.LogRegistry'

    @TempDir
    File tempDir

    def "getRuntimeClasspathCacheInputs drops jar entries lacking whitelist resource while keeping directories and jars that bear it"() {
        given:
        def project = ProjectBuilder.builder().withName("whitelist-cache-input-filter").build()
        def dirWithWhitelist = new File(tempDir, "dir-with-whitelist")
        new File(dirWithWhitelist, PrunusPluginConstants.WHITELIST_RESOURCE_PATH).with {
            parentFile.mkdirs()
            text = "com.example.Type\n"
        }
        def jarWithWhitelist = createJarWithEntries(
                "lib-with-whitelist.jar",
                [PrunusPluginConstants.WHITELIST_RESOURCE_PATH])
        def jarWithoutWhitelist = createJarWithEntries(
                "lib-without-whitelist.jar",
                ["META-INF/other.txt"])

        def task = project.tasks.register("testWhitelistCacheInputs", GenerateLibraryWhitelistTask) { t ->
            t.runtimeClasspath.from(
                    dirWithWhitelist,
                    jarWithWhitelist,
                    jarWithoutWhitelist)
        }.get()

        when:
        def cacheInputs = task.runtimeClasspathCacheInputs

        then:
        def absolutePaths = cacheInputs.collect { it.absolutePath } as Set
        absolutePaths == [dirWithWhitelist.canonicalFile.absolutePath, jarWithWhitelist.canonicalFile.absolutePath] as Set
        !absolutePaths.contains(jarWithoutWhitelist.canonicalFile.absolutePath)
    }

    def "generate writes whitelist metadata when classes directory and registry are valid"() {
        given:
        def project = ProjectBuilder.builder().withName("whitelist-generate-success").build()
        def compiled = copyRegistryClassBytes(TEST_REGISTRY_CLASS)
        def outputDir = new File(tempDir, "generated-whitelist-success")
        def task = project.tasks.register("testWhitelistGenerateSuccess", GenerateLibraryWhitelistTask) { t ->
            t.registryClass.set(compiled.registryClassName)
            t.targetCompatibility.set("25")
            t.mainClassesDirs.from(compiled.classesDir)
            t.outputDirectory.set(outputDir)
        }.get()

        when:
        task.generate()

        then:
        def whitelistFile = new File(outputDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistFile.exists()
        whitelistFile.text.contains("java.lang.CharSequence")
    }

    def "generate exits without failure when classes directories are empty or missing"() {
        given:
        def project = ProjectBuilder.builder().withName("whitelist-generate-empty").build()
        def missingClassesDir = new File(tempDir, "missing-classes")
        def outputDir = new File(tempDir, "generated-whitelist-empty")
        def task = project.tasks.register("testWhitelistGenerateEmpty", GenerateLibraryWhitelistTask) { t ->
            t.mainClassesDirs.from(missingClassesDir)
        }.get()

        when:
        task.generate()

        then:
        !new File(outputDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH).exists()
        !outputDir.exists()
    }

    @IgnoreIf({ !Files.getFileStore(new File(System.getProperty("java.io.tmpdir")).toPath()).supportsFileAttributeView("posix") })
    def "generate wraps IO failure from registry metadata writer in GradleException with task message"() {
        given:
        def project = ProjectBuilder.builder().withName("whitelist-generate-io-failure").build()
        def compiled = copyRegistryClassBytes(TEST_REGISTRY_CLASS)
        def outputDir = new File(tempDir, "readonly-output")
        outputDir.mkdirs()
        Files.setPosixFilePermissions(outputDir.toPath(),
                PosixFilePermissions.fromString("r-xr-xr-x"))
        def task = project.tasks.register("testWhitelistGenerateIoFailure", GenerateLibraryWhitelistTask) { t ->
            t.registryClass.set(compiled.registryClassName)
            t.targetCompatibility.set("25")
            t.mainClassesDirs.from(compiled.classesDir)
            t.outputDirectory.set(outputDir)
        }.get()

        when:
        task.generate()

        then:
        def ex = thrown(GradleException)
        ex.message == "Failed to generate library whitelist"
        ex.cause instanceof IllegalStateException
        ex.cause.message.startsWith("Failed to write whitelist file:")
        !new File(outputDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH).exists()

        cleanup:
        if (outputDir.exists()) {
            Files.setPosixFilePermissions(outputDir.toPath(),
                    PosixFilePermissions.fromString("rwxr-xr-x"))
        }
    }

    def "generate wraps IllegalStateException from upstream classpath locator in GradleException with task message"() {
        given:
        def project = ProjectBuilder.builder().withName("whitelist-generate-ise").build()
        def compiled = copyRegistryClassBytes(TEST_REGISTRY_CLASS)
        def corruptJar = new File(tempDir, "corrupt-runtime-classpath.jar")
        corruptJar.bytes = [] as byte[]
        def outputDir = new File(tempDir, "generated-whitelist-ise")
        def task = project.tasks.register("testWhitelistGenerateIse", GenerateLibraryWhitelistTask) { t ->
            t.registryClass.set(compiled.registryClassName)
            t.targetCompatibility.set("25")
            t.mainClassesDirs.from(compiled.classesDir)
            t.runtimeClasspath.from(corruptJar)
            t.outputDirectory.set(outputDir)
        }.get()

        when:
        task.generate()

        then:
        def ex = thrown(GradleException)
        ex.message == "Failed to generate library whitelist"
        ex.cause instanceof IllegalStateException
        ex.cause.message.startsWith("Failed to open JAR file: ")
        ex.cause.message.contains(corruptJar.absolutePath)
        !new File(outputDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH).exists()
    }

    def "contributesToWhitelist routes directory entries through as candidates"() {
        given:
        def entry = new File(tempDir, "empty-dir")
        entry.mkdirs()

        expect:
        GenerateLibraryWhitelistTask.contributesToWhitelist(entry)
    }

    def "contributesToWhitelist rejects non-jar plain files"() {
        given:
        def entry = writeBytes("plain.txt", "noise".bytes)

        expect:
        !GenerateLibraryWhitelistTask.contributesToWhitelist(entry)
    }

    def "contributesToWhitelist accepts jar files bearing the whitelist resource"() {
        given:
        def entry = createJarWithEntries("ok.jar", [PrunusPluginConstants.WHITELIST_RESOURCE_PATH])

        expect:
        GenerateLibraryWhitelistTask.contributesToWhitelist(entry)
    }

    def "contributesToWhitelist rejects jar files lacking the whitelist resource"() {
        given:
        def entry = createJarWithEntries("none.jar", ["META-INF/other"])

        expect:
        !GenerateLibraryWhitelistTask.contributesToWhitelist(entry)
    }

    def "contributesToWhitelist rejects corrupt jar files that fail to open"() {
        given:
        def entry = writeBytes("corrupt.jar", "not a zip".bytes)

        expect:
        !GenerateLibraryWhitelistTask.contributesToWhitelist(entry)
    }

    private File createJarWithEntries(String name, List<String> entryPaths) {
        def jar = new File(tempDir, name)
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { jos ->
            entryPaths.each { path ->
                jos.putNextEntry(new JarEntry(path))
                jos.closeEntry()
            }
        }
        jar
    }

    private File writeBytes(String name, byte[] payload) {
        def file = new File(tempDir, name)
        file.bytes = payload
        file
    }

    private Map<String, Object> copyRegistryClassBytes(String registryClassName) {
        def classesDir = new File(tempDir, "classes-copied")
        classesDir.mkdirs()

        copyClassBytesTo(classesDir, registryClassName)
        copyClassBytesTo(classesDir, LOG_REGISTRY_ANNOTATION_CLASS)

        [classesDir: classesDir, registryClassName: registryClassName]
    }

    private void copyClassBytesTo(File rootDir, String className) {
        def classResourcePath = className.replace('.', '/') + ".class"
        def classTargetFile = new File(rootDir, classResourcePath)
        classTargetFile.parentFile.mkdirs()

        def stream = getClass().classLoader.getResourceAsStream(classResourcePath)
        assert stream != null
        stream.withCloseable { input ->
            classTargetFile.withOutputStream { output ->
                output << input
            }
        }
    }
}
