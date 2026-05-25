package org.libprunus.core.plugin.aot.task

import java.io.UncheckedIOException
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import spock.lang.Specification
import spock.lang.TempDir

class LogConfigProviderScannerSpec extends Specification {

    @TempDir
    File tempDir

    static final String SPI_NAME = "org.libprunus.core.log.runtime.AbstractLogConfig"
    static final String SPI_PATH = "META-INF/services/$SPI_NAME"
    static final String CLASS_NAME = "org.libprunus.aot.generated.abc.LogConfigBindingImpl"
    static final String CLASS_PATH = "org/libprunus/aot/generated/abc/LogConfigBindingImpl.class"

    def cleanup() {
        LogConfigProviderScanner.JAR_INDEX_CACHE.clear()
    }

    def "scan throws NullPointerException identifying the classpathEntries parameter when classpathEntries is null"() {
        given:
        def request = new LogConfigProviderScanner.ScanRequest(SPI_NAME, CLASS_NAME)

        when:
        LogConfigProviderScanner.scan(null, request)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "classpathEntries"
    }

    def "scan throws NullPointerException identifying the request parameter when request is null"() {
        given:
        def classpath = [tempDir] as Set

        when:
        LogConfigProviderScanner.scan(classpath, null)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "request"
    }

    def "scan collects provider and class sources in one pass without cross-contamination between facets"() {
        given:
        def providerOnlyDir = new File(tempDir, "provider-only-dir")
        def spiFile = new File(new File(new File(providerOnlyDir, "META-INF"), "services"), SPI_NAME)
        spiFile.parentFile.mkdirs()
        spiFile.text = "com.example.DirProvider"

        def classOnlyJar = createJar("class-only.jar", [CLASS_PATH])
        def classpath = [providerOnlyDir, classOnlyJar] as LinkedHashSet

        when:
        def result = LogConfigProviderScanner.scan(
                classpath,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, CLASS_NAME))

        then:
        result.providerSources() == [providerOnlyDir.absolutePath]
        result.classSources() == [classOnlyJar.absolutePath]
        !result.providerSources().contains(classOnlyJar.absolutePath)
        !result.classSources().contains(providerOnlyDir.absolutePath)
    }

    def "scan returns provider source list in deterministic portable-path order regardless of insertion order"() {
        given:
        def dirB = new File(tempDir, "b-entry")
        def dirA = new File(tempDir, "a-entry")
        [dirB, dirA].each { dir ->
            def spiFile = new File(new File(new File(dir, "META-INF"), "services"), SPI_NAME)
            spiFile.parentFile.mkdirs()
            spiFile.text = "com.example.Provider"
        }
        def classpathEntries = [dirB, dirA] as LinkedHashSet

        when:
        def result = LogConfigProviderScanner.scan(
                classpathEntries,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))

        then:
        result.providerSources() == [dirA.absolutePath, dirB.absolutePath]
    }

    def "scan handles equivalent jar path aliases in one pass"() {
        given:
        def jar = createJar("aliased-entry.jar", [SPI_PATH])
        def alias = new File(tempDir, "./aliased-entry.jar")
        def classpathEntries = [jar, alias] as LinkedHashSet

        when:
        def result = LogConfigProviderScanner.scan(
                classpathEntries,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null)).providerSources()

        then:
        result.size() == 2
        result.contains(jar.absolutePath)
        result.contains(alias.absolutePath)
    }

    def "scan skips non-existent entries across both provider and class facets"() {
        given:
        def missing = new File(tempDir, "missing.jar")

        when:
        def result = LogConfigProviderScanner.scan(
                [missing] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, CLASS_NAME))

        then:
        result.providerSources().isEmpty()
        result.classSources().isEmpty()
    }

    def "scan ignores classpath entries that are neither directories nor jar files"() {
        given:
        def stray = new File(tempDir, "stray.txt")
        stray.text = "noise"
        def validDir = new File(tempDir, "valid-dir")
        def spiFile = new File(new File(new File(validDir, "META-INF"), "services"), SPI_NAME)
        spiFile.parentFile.mkdirs()
        spiFile.text = "com.example.Provider"

        when:
        def strayOnly = LogConfigProviderScanner.scan(
                [stray] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, CLASS_NAME))
        def mixed = LogConfigProviderScanner.scan(
                [stray, validDir] as LinkedHashSet,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))

        then:
        strayOnly.providerSources().isEmpty()
        strayOnly.classSources().isEmpty()
        mixed.providerSources() == [validDir.absolutePath]
        mixed.classSources().isEmpty()
    }

    def "scan throws SecurityException when directory entry SPI service name contains directory traversal sequences"() {
        given:
        def entryRoot = new File(tempDir, "scan-traversal")
        entryRoot.mkdirs()
        def maliciousSpiName = "../../../etc/passwd"
        def cache = LogConfigProviderScanner.JAR_INDEX_CACHE

        when:
        LogConfigProviderScanner.scan(
                [entryRoot] as Set,
                new LogConfigProviderScanner.ScanRequest(maliciousSpiName, null))

        then:
        def ex = thrown(SecurityException)
        ex.message.contains("Directory traversal detected in path:")
        ex.message.contains("META-INF/services/" + maliciousSpiName)
        cache.isEmpty()
    }

    def "scan reports no provider source when directory entry resource path resolves to a directory rather than a regular file"() {
        given:
        def entryRoot = new File(tempDir, "resource-as-dir")
        def resourceAsDir = new File(new File(new File(entryRoot, "META-INF"), "services"), SPI_NAME)
        resourceAsDir.mkdirs()

        when:
        def result = LogConfigProviderScanner.scan(
                [entryRoot] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))

        then:
        result.providerSources().isEmpty()
    }

    def "scan aborts and produces no partial results when any jar in classpath fails to open"() {
        given:
        def validDir = new File(tempDir, "valid-entry-before-broken")
        def spiFile = new File(new File(new File(validDir, "META-INF"), "services"), SPI_NAME)
        spiFile.parentFile.mkdirs()
        spiFile.text = "com.example.Provider"
        def brokenJar = new File(tempDir, "broken-mixed.jar")
        brokenJar.bytes = [] as byte[]

        when:
        LogConfigProviderScanner.scan(
                [validDir, brokenJar] as LinkedHashSet,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))

        then:
        thrown(UncheckedIOException)

        and:
        def baseline = LogConfigProviderScanner.scan(
                [validDir] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))
        baseline.providerSources() == [validDir.absolutePath]
    }

    def "scan includes jar path in IOException context when jar content is invalid"() {
        given:
        def jar = new File(tempDir, "broken.jar")
        jar.bytes = [] as byte[]

        when:
        LogConfigProviderScanner.scan(
                [jar] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))

        then:
        def ex = thrown(UncheckedIOException)
        ex.message.contains("Failed to scan JAR file for SPI/metadata")
        ex.message.contains(jar.absolutePath)
    }

    def "scan ignores jar entries whose name starts with absolute path separators while still surfacing safe entries"() {
        given:
        def mixedJar = createJar("mixed-prefix.jar", ["/" + SPI_PATH, "\\" + SPI_PATH, SPI_PATH])
        def unsafeOnlyJar = createJar("unsafe-only.jar", ["/" + SPI_PATH, "\\" + SPI_PATH])

        when:
        def mixedResult = LogConfigProviderScanner.scan(
                [mixedJar] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))
        def unsafeResult = LogConfigProviderScanner.scan(
                [unsafeOnlyJar] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))

        then:
        mixedResult.providerSources() == [mixedJar.absolutePath]
        unsafeResult.providerSources().isEmpty()
    }

    def "scan ignores jar entries whose name contains parent-directory traversal segments"() {
        given:
        def jar = createJar("parent-traversal.jar", ["META-INF/services/../services/$SPI_NAME" as String])

        when:
        def result = LogConfigProviderScanner.scan(
                [jar] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))

        then:
        result.providerSources().isEmpty()
    }

    def "scan reuses cached JarEntryIndex when same jar is scanned twice with unchanged mtime and size"() {
        given:
        def jar = createJar("cache-hit-stable.jar", [SPI_PATH])
        def cache = LogConfigProviderScanner.JAR_INDEX_CACHE

        when:
        def firstResult = LogConfigProviderScanner.scan(
                [jar] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null)).providerSources()
        def keyAfterFirst = cache.keySet().find { it.absolutePath == jar.absolutePath }
        def indexAfterFirst = cache.get(keyAfterFirst)
        def secondResult = LogConfigProviderScanner.scan(
                [jar] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null)).providerSources()
        def keyAfterSecond = cache.keySet().find { it.absolutePath == jar.absolutePath }
        def indexAfterSecond = cache.get(keyAfterSecond)

        then:
        firstResult == [jar.absolutePath]
        secondResult == [jar.absolutePath]
        indexAfterFirst != null
        indexAfterFirst.is(indexAfterSecond)
        cache.size() == 1
    }

    def "scan invalidates cached JarEntryIndex when jar lastModified changes"() {
        given:
        def jar = createJar("cache-invalidate-mtime.jar", [SPI_PATH])
        def cache = LogConfigProviderScanner.JAR_INDEX_CACHE

        when:
        LogConfigProviderScanner.scan(
                [jar] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))
        def firstKey = cache.keySet().find { it.absolutePath == jar.absolutePath }
        def firstIndex = cache.get(firstKey)
        def shiftedMtime = jar.lastModified() + 5000L
        assert jar.setLastModified(shiftedMtime)
        LogConfigProviderScanner.scan(
                [jar] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))
        def matchingKeys = cache.keySet().findAll { it.absolutePath == jar.absolutePath }
        def secondKey = matchingKeys.find { it.lastModified == shiftedMtime }
        def secondIndex = cache.get(secondKey)

        then:
        firstKey != null
        secondKey != null
        firstKey.lastModified != secondKey.lastModified
        firstIndex != null
        secondIndex != null
        !firstIndex.is(secondIndex)
        matchingKeys.size() == 2
    }

    def "scan invalidates cached JarEntryIndex when jar size changes"() {
        given:
        def jar = createJar("cache-invalidate-size.jar", [SPI_PATH])
        def cache = LogConfigProviderScanner.JAR_INDEX_CACHE

        when:
        LogConfigProviderScanner.scan(
                [jar] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))
        def firstKey = cache.keySet().find { it.absolutePath == jar.absolutePath }
        def firstIndex = cache.get(firstKey)
        def originalMtime = jar.lastModified()
        rewriteJar(jar, [SPI_PATH, "extra/entry-${UUID.randomUUID()}.txt"])
        assert jar.setLastModified(originalMtime)
        assert jar.length() != firstKey.size
        LogConfigProviderScanner.scan(
                [jar] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))
        def matchingKeys = cache.keySet().findAll { it.absolutePath == jar.absolutePath }
        def secondKey = matchingKeys.find { it.size == jar.length() && it.size != firstKey.size }
        def secondIndex = cache.get(secondKey)

        then:
        firstKey != null
        secondKey != null
        firstKey.size != secondKey.size
        firstIndex != null
        secondIndex != null
        !firstIndex.is(secondIndex)
        matchingKeys.size() == 2
    }

    def "scan does not cache JarEntryIndex when buildJarEntryIndex throws UncheckedIOException"() {
        given:
        def brokenJar = new File(tempDir, "broken-no-poison.jar")
        brokenJar.bytes = [] as byte[]
        def cache = LogConfigProviderScanner.JAR_INDEX_CACHE

        when:
        LogConfigProviderScanner.scan(
                [brokenJar] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))

        then:
        thrown(UncheckedIOException)
        !cache.keySet().any { it.absolutePath == brokenJar.absolutePath }
        cache.isEmpty()

        when:
        LogConfigProviderScanner.scan(
                [brokenJar] as Set,
                new LogConfigProviderScanner.ScanRequest(SPI_NAME, null))

        then:
        thrown(UncheckedIOException)
        !cache.keySet().any { it.absolutePath == brokenJar.absolutePath }
        cache.isEmpty()
    }

    private File createJar(String name, List<String> entries) {
        def jar = new File(tempDir, name)
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { jos ->
            entries.each { entry ->
                jos.putNextEntry(new JarEntry(entry))
                jos.closeEntry()
            }
        }
        jar
    }

    private void rewriteJar(File jar, List<String> entries) {
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { jos ->
            entries.each { entry ->
                jos.putNextEntry(new JarEntry(entry))
                jos.closeEntry()
            }
        }
    }
}
