package org.libprunus.core.plugin.aot.task

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import spock.lang.Specification
import spock.lang.TempDir

class RuntimeClasspathInputSelectorSpec extends Specification {

    private static final String BINDING_CLASS = "org.example.Binding"
    private static final String BINDING_CLASS_PATH = BINDING_CLASS.replace('.', '/') + ".class"
    private static final String SPI_MARKER_PATH =
            PrunusPluginConstants.SPI_SERVICES_DIR + "/" + PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN

    @TempDir
    File tempDir

    def "selectGenerateCacheInputs rejects null classpath set"() {
        when:
        RuntimeClasspathInputSelector.selectGenerateCacheInputs(null, false)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "runtimeClasspathEntries"
    }

    def "selectGenerateCacheInputs returns empty list when explicit binding is true even with whitelist-bearing entries"() {
        given:
        def dirWithWhitelist = new File(tempDir, "with-whitelist")
        def whitelistFile = new File(dirWithWhitelist, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistFile.parentFile.mkdirs()
        whitelistFile.text = "sample.Type\n"
        def jar = createJar("any.jar", [:])

        when:
        def inputs = RuntimeClasspathInputSelector.selectGenerateCacheInputs(
                [dirWithWhitelist, jar] as Set,
                true)

        then:
        inputs == List.of()
    }

    def "selectGenerateCacheInputs skips non-existent entries while still selecting valid sibling entries"() {
        given:
        def missing = new File(tempDir, "ghost-dir")
        def baseline = new File(tempDir, "baseline-with-whitelist")
        def whitelistFile = new File(baseline, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistFile.parentFile.mkdirs()
        whitelistFile.text = "sample.Type\n"

        when:
        def inputs = RuntimeClasspathInputSelector.selectGenerateCacheInputs(
                [missing, baseline] as Set,
                false)

        then:
        inputs == [normalize(baseline)]
    }

    def "selectGenerateCacheInputs keeps directories with whitelist and excludes directories without whitelist"() {
        given:
        def dirWithWhitelist = new File(tempDir, "dir-with-whitelist")
        def whitelistFile = new File(dirWithWhitelist, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistFile.parentFile.mkdirs()
        whitelistFile.text = "sample.Type\n"

        def dirWithoutWhitelist = new File(tempDir, "dir-without-whitelist")
        dirWithoutWhitelist.mkdirs()

        when:
        def inputs = RuntimeClasspathInputSelector.selectGenerateCacheInputs(
                [dirWithWhitelist, dirWithoutWhitelist] as Set,
                false)

        then:
        inputs == [normalize(dirWithWhitelist)]
    }

    def "selectGenerateCacheInputs keeps every jar file regardless of contents"() {
        given:
        def jarWithWhitelist = createJar("a-with-whitelist.jar", [
                (PrunusPluginConstants.WHITELIST_RESOURCE_PATH): "sample.Type\n"
        ])
        def jarWithoutWhitelist = createJar("b-without-whitelist.jar", [
                "META-INF/services/example": "sample.Provider\n"
        ])

        when:
        def inputs = RuntimeClasspathInputSelector.selectGenerateCacheInputs(
                [jarWithWhitelist, jarWithoutWhitelist] as Set,
                false)

        then:
        inputs == [normalize(jarWithWhitelist), normalize(jarWithoutWhitelist)]
    }

    def "selectGenerateCacheInputs ignores plain regular files while still selecting valid sibling entries"() {
        given:
        def plainFile = new File(tempDir, "README.txt")
        plainFile.text = "not a jar"
        def baseline = new File(tempDir, "baseline-with-whitelist")
        def whitelistFile = new File(baseline, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistFile.parentFile.mkdirs()
        whitelistFile.text = "sample.Type\n"

        when:
        def inputs = RuntimeClasspathInputSelector.selectGenerateCacheInputs(
                [plainFile, baseline] as Set,
                false)

        then:
        inputs == [normalize(baseline)]
    }

    def "selectGenerateCacheInputs ignores files with uppercase .JAR extension while still selecting lowercase .jar siblings"() {
        given:
        def uppercaseJar = new File(tempDir, "foo.JAR")
        uppercaseJar.bytes = new byte[0]
        def lowercaseJar = createJar("foo.jar", [:])

        when:
        def inputs = RuntimeClasspathInputSelector.selectGenerateCacheInputs(
                [uppercaseJar, lowercaseJar] as Set,
                false)

        then:
        inputs == [normalize(lowercaseJar)]
    }

    def "selectGenerateCacheInputs includes unreadable jar without opening it"() {
        given:
        def unreadableJar = new File(tempDir, "broken-generate.jar")
        unreadableJar.bytes = [] as byte[]

        when:
        def inputs = RuntimeClasspathInputSelector.selectGenerateCacheInputs([unreadableJar] as Set, false)

        then:
        inputs == [normalize(unreadableJar)]
    }

    def "selectGenerateCacheInputs returns inputs as normalized absolute File objects sorted by absolute path ascending"() {
        given:
        def dirZ = new File(tempDir, "z-dir")
        def whitelistZ = new File(dirZ, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistZ.parentFile.mkdirs()
        whitelistZ.text = "sample.Type\n"

        def dirA = new File(tempDir, "a-dir")
        def whitelistA = new File(dirA, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistA.parentFile.mkdirs()
        whitelistA.text = "sample.Type\n"

        def jarM = createJar("m.jar", [:])

        when:
        def inputs = RuntimeClasspathInputSelector.selectGenerateCacheInputs(
                [dirZ, jarM, dirA] as Set,
                false)

        then:
        inputs == [normalize(dirA), normalize(jarM), normalize(dirZ)]
    }

    def "selectGenerateCacheInputs normalizes input File path lexically removing dot-segments"() {
        given:
        def dirWithWhitelist = new File(tempDir, "dir-with-whitelist")
        def whitelistFile = new File(dirWithWhitelist, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistFile.parentFile.mkdirs()
        whitelistFile.text = "sample.Type\n"
        def sibling = new File(tempDir, "sibling")
        sibling.mkdirs()

        def messyInput = new File(tempDir, "./sibling/../dir-with-whitelist")

        when:
        def inputs = RuntimeClasspathInputSelector.selectGenerateCacheInputs([messyInput] as Set, false)

        then:
        inputs == [normalize(dirWithWhitelist)]
        !inputs[0].absolutePath.contains("/./")
        !inputs[0].absolutePath.contains("/../")
    }

    def "selectResolveCacheInputs rejects null arguments"() {
        when:
        RuntimeClasspathInputSelector.selectResolveCacheInputs(classpathEntries, selectedBindingClass)

        then:
        def ex = thrown(NullPointerException)
        ex.message == expectedMessage

        where:
        classpathEntries       | selectedBindingClass | expectedMessage
        null                   | BINDING_CLASS        | "runtimeClasspathEntries"
        Collections.emptySet() | null                 | "selectedBindingClass"
    }

    def "selectResolveCacheInputs skips non-existent entries while still selecting valid sibling entries"() {
        given:
        def missing = new File(tempDir, "ghost-resolve-dir")
        def baseline = new File(tempDir, "baseline-with-binding")
        def bindingFile = new File(baseline, BINDING_CLASS_PATH)
        bindingFile.parentFile.mkdirs()
        bindingFile.bytes = new byte[0]

        when:
        def inputs = RuntimeClasspathInputSelector.selectResolveCacheInputs(
                [missing, baseline] as Set,
                BINDING_CLASS)

        then:
        inputs == [normalize(baseline)]
    }

    def "selectResolveCacheInputs keeps directory when at least one of binding class file or SPI marker exists"() {
        given:
        def dir = new File(tempDir, dirName)
        dir.mkdirs()
        def marker = new File(dir, markerPath)
        marker.parentFile.mkdirs()
        marker.text = "present"

        when:
        def inputs = RuntimeClasspathInputSelector.selectResolveCacheInputs([dir] as Set, BINDING_CLASS)

        then:
        inputs == [normalize(dir)]

        where:
        dirName                 | markerPath
        "dir-with-binding-only" | BINDING_CLASS_PATH
        "dir-with-spi-only"     | SPI_MARKER_PATH
    }

    def "selectResolveCacheInputs keeps directory carrying both binding class file and SPI marker as a single entry"() {
        given:
        def dir = new File(tempDir, "dir-with-all-markers")
        def bindingFile = new File(dir, BINDING_CLASS_PATH)
        bindingFile.parentFile.mkdirs()
        bindingFile.bytes = new byte[0]
        def spiFile = new File(dir, SPI_MARKER_PATH)
        spiFile.parentFile.mkdirs()
        spiFile.text = "sample.Provider\n"

        when:
        def inputs = RuntimeClasspathInputSelector.selectResolveCacheInputs([dir] as Set, BINDING_CLASS)

        then:
        inputs == [normalize(dir)]
    }

    def "selectResolveCacheInputs excludes directory that has none of the resolve markers while still selecting valid sibling entries"() {
        given:
        def emptyDir = new File(tempDir, "no-markers")
        emptyDir.mkdirs()
        def baseline = new File(tempDir, "baseline-with-binding")
        def bindingFile = new File(baseline, BINDING_CLASS_PATH)
        bindingFile.parentFile.mkdirs()
        bindingFile.bytes = new byte[0]

        when:
        def inputs = RuntimeClasspathInputSelector.selectResolveCacheInputs(
                [emptyDir, baseline] as Set,
                BINDING_CLASS)

        then:
        inputs == [normalize(baseline)]
    }

    def "selectResolveCacheInputs keeps every jar regardless of contents (cache-input fingerprint does not open jars)"() {
        given:
        def jarWithBinding = createJar("a-with-binding.jar", [(BINDING_CLASS_PATH): ""])
        def jarUnrelated = createJar("b-unrelated.jar", ["sample/Other.class": ""])

        when:
        def inputs = RuntimeClasspathInputSelector.selectResolveCacheInputs(
                [jarWithBinding, jarUnrelated] as Set,
                BINDING_CLASS)

        then:
        inputs == [normalize(jarWithBinding), normalize(jarUnrelated)]
    }

    def "selectResolveCacheInputs includes unreadable jar without opening it"() {
        given:
        def unreadableJar = new File(tempDir, "broken-resolve.jar")
        unreadableJar.bytes = [] as byte[]

        when:
        def inputs = RuntimeClasspathInputSelector.selectResolveCacheInputs(
                [unreadableJar] as Set,
                BINDING_CLASS)

        then:
        inputs == [normalize(unreadableJar)]
    }

    def "selectResolveCacheInputs ignores plain regular files while still selecting valid sibling entries"() {
        given:
        def plainFile = new File(tempDir, "README-resolve.txt")
        plainFile.text = "not a jar"
        def baseline = new File(tempDir, "baseline-with-binding")
        def bindingFile = new File(baseline, BINDING_CLASS_PATH)
        bindingFile.parentFile.mkdirs()
        bindingFile.bytes = new byte[0]

        when:
        def inputs = RuntimeClasspathInputSelector.selectResolveCacheInputs(
                [plainFile, baseline] as Set,
                BINDING_CLASS)

        then:
        inputs == [normalize(baseline)]
    }

    def "selectResolveCacheInputs returns inputs as normalized absolute File objects sorted by absolute path ascending"() {
        given:
        def dirZ = new File(tempDir, "z-resolve-dir")
        def bindingZ = new File(dirZ, BINDING_CLASS_PATH)
        bindingZ.parentFile.mkdirs()
        bindingZ.bytes = new byte[0]

        def dirA = new File(tempDir, "a-resolve-dir")
        def spiA = new File(dirA, SPI_MARKER_PATH)
        spiA.parentFile.mkdirs()
        spiA.text = "sample.Provider\n"

        def jarM = createJar("m-resolve.jar", [(BINDING_CLASS_PATH): ""])

        when:
        def inputs = RuntimeClasspathInputSelector.selectResolveCacheInputs(
                [dirZ, jarM, dirA] as Set,
                BINDING_CLASS)

        then:
        inputs == [normalize(dirA), normalize(jarM), normalize(dirZ)]
    }

    def "selectResolveScanEntries rejects null arguments"() {
        when:
        RuntimeClasspathInputSelector.selectResolveScanEntries(classpathEntries, selectedBindingClass)

        then:
        def ex = thrown(NullPointerException)
        ex.message == expectedMessage

        where:
        classpathEntries       | selectedBindingClass | expectedMessage
        null                   | BINDING_CLASS        | "runtimeClasspathEntries"
        Collections.emptySet() | null                 | "selectedBindingClass"
    }

    def "selectResolveScanEntries skips non-existent entries while still selecting valid sibling entries"() {
        given:
        def missing = new File(tempDir, "ghost-scan-dir")
        def baseline = new File(tempDir, "baseline-with-binding")
        def bindingFile = new File(baseline, BINDING_CLASS_PATH)
        bindingFile.parentFile.mkdirs()
        bindingFile.bytes = new byte[0]

        when:
        def scan = RuntimeClasspathInputSelector.selectResolveScanEntries(
                [missing, baseline] as Set,
                BINDING_CLASS)

        then:
        new ArrayList<>(scan) == [baseline]
    }

    def "selectResolveScanEntries dispatches dir-with-binding / dir-with-spi / jar-with-spi as scan-relevant and excludes unrelated entries"() {
        given:
        def dirWithBinding = new File(tempDir, "dir-with-binding")
        def bindingClassFile = new File(dirWithBinding, BINDING_CLASS_PATH)
        bindingClassFile.parentFile.mkdirs()
        bindingClassFile.bytes = new byte[0]

        def dirWithSpi = new File(tempDir, "dir-with-spi")
        def spiFile = new File(dirWithSpi, SPI_MARKER_PATH)
        spiFile.parentFile.mkdirs()
        spiFile.text = "sample.Provider\n"

        def unrelatedDir = new File(tempDir, "dir-unrelated")
        unrelatedDir.mkdirs()

        def jarWithSpi = createJar("jar-with-spi.jar", [
                (SPI_MARKER_PATH): "sample.Provider\n"
        ])
        def jarUnrelated = createJar("jar-unrelated.jar", ["sample/Other.class": ""])

        when:
        def scanEntries = RuntimeClasspathInputSelector.selectResolveScanEntries(
                [dirWithBinding, dirWithSpi, unrelatedDir, jarWithSpi, jarUnrelated] as Set,
                BINDING_CLASS)

        then:
        scanEntries.contains(dirWithBinding)
        scanEntries.contains(dirWithSpi)
        scanEntries.contains(jarWithSpi)
        !scanEntries.contains(unrelatedDir)
        !scanEntries.contains(jarUnrelated)
        scanEntries.size() == 3
    }

    def "selectResolveScanEntries includes jars matching each resolve marker"() {
        given:
        def jarWithBinding = createJar("scan-with-binding.jar", [(BINDING_CLASS_PATH): ""])
        def jarWithSpi = createJar("scan-with-spi.jar", [
                (SPI_MARKER_PATH): "sample.Provider\n"
        ])

        when:
        def scanEntries = RuntimeClasspathInputSelector.selectResolveScanEntries(
                [jarWithBinding, jarWithSpi] as Set,
                BINDING_CLASS)

        then:
        scanEntries.contains(jarWithBinding)
        scanEntries.contains(jarWithSpi)
        scanEntries.size() == 2
    }

    def "selectResolveScanEntries excludes readable jar that contains neither binding class entry nor SPI marker entry"() {
        given:
        def jarUnrelated = createJar("scan-unrelated.jar", ["sample/Other.class": ""])
        def baseline = createJar("scan-with-binding.jar", [(BINDING_CLASS_PATH): ""])

        when:
        def scanEntries = RuntimeClasspathInputSelector.selectResolveScanEntries(
                [jarUnrelated, baseline] as Set,
                BINDING_CLASS)

        then:
        scanEntries.contains(baseline)
        !scanEntries.contains(jarUnrelated)
        scanEntries.size() == 1
    }

    def "selectResolveScanEntries throws UncheckedIOException with jar absolute path in message when jar is unreadable"() {
        given:
        def unreadableJar = new File(tempDir, "broken-scan.jar")
        unreadableJar.bytes = [] as byte[]

        when:
        RuntimeClasspathInputSelector.selectResolveScanEntries([unreadableJar] as Set, BINDING_CLASS)

        then:
        def ex = thrown(UncheckedIOException)
        ex.message.contains("Failed to inspect JAR entry")
        ex.message.contains(unreadableJar.absolutePath)
    }

    def "selectResolveScanEntries ignores plain regular files while still selecting valid sibling entries"() {
        given:
        def plainFile = new File(tempDir, "README-scan.txt")
        plainFile.text = "not a jar"
        def baseline = new File(tempDir, "baseline-with-binding")
        def bindingFile = new File(baseline, BINDING_CLASS_PATH)
        bindingFile.parentFile.mkdirs()
        bindingFile.bytes = new byte[0]

        when:
        def scan = RuntimeClasspathInputSelector.selectResolveScanEntries(
                [plainFile, baseline] as Set,
                BINDING_CLASS)

        then:
        new ArrayList<>(scan) == [baseline]
    }

    def "selectResolveScanEntries preserves classpath insertion order"() {
        given:
        def zDirWithBinding = new File(tempDir, "z-scan-dir")
        def bindingZ = new File(zDirWithBinding, BINDING_CLASS_PATH)
        bindingZ.parentFile.mkdirs()
        bindingZ.bytes = new byte[0]

        def jarWithSpi = createJar("middle-scan.jar", [
                (SPI_MARKER_PATH): "sample.Provider\n"
        ])

        def aDirWithBinding = new File(tempDir, "a-scan-dir")
        def bindingA = new File(aDirWithBinding, BINDING_CLASS_PATH)
        bindingA.parentFile.mkdirs()
        bindingA.bytes = new byte[0]

        def entries = new LinkedHashSet<File>()
        entries.add(zDirWithBinding)
        entries.add(jarWithSpi)
        entries.add(aDirWithBinding)

        when:
        def scan = RuntimeClasspathInputSelector.selectResolveScanEntries(entries, BINDING_CLASS)

        then:
        new ArrayList<>(scan) == [zDirWithBinding, jarWithSpi, aDirWithBinding]
    }

    private static File normalize(File file) {
        file.toPath().normalize().toAbsolutePath().toFile()
    }

    private File createJar(String name, Map<String, String> entries) {
        def jar = new File(tempDir, name)
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { jos ->
            entries.each { path, content ->
                jos.putNextEntry(new JarEntry(path))
                if (content != null && !content.isEmpty()) {
                    jos.write(content.getBytes("UTF-8"))
                }
                jos.closeEntry()
            }
        }
        jar
    }
}
