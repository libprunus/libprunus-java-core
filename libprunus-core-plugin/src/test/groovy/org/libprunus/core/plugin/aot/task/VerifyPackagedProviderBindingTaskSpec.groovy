package org.libprunus.core.plugin.aot.task

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.libprunus.core.plugin.aot.BindingIdSanitizer
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import org.libprunus.core.plugin.aot.util.ResourceLimitExceededException
import spock.lang.Specification
import spock.lang.TempDir

class VerifyPackagedProviderBindingTaskSpec extends Specification {

    private static final String SPI_PATH = "META-INF/services/" + PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN

    @TempDir
    File tempDir

    def "verify task fails early when explicit binding class is invalid"() {
        given:
        def archive = createJar("verify-invalid-explicit.jar", [:])
        def task = createTask("verifyInvalidExplicit", archive, "b123", explicit)

        when:
        task.verify()

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains(expectedFragment)
        ex.message.contains(explicit)

        where:
        explicit                    || expectedFragment
        "my.invalid-binding.Class"  || "not a valid Java FQCN"
        "java.lang.CustomBinding"   || "reserved package namespace"
    }

    def "verify task falls back to default binding when explicit binding is whitespace"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def jar = createJar("blank-explicit.jar", [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClassPath("b123")): "",
                (SPI_PATH): "${bindingClass}\n"
        ])
        def task = createTask("verifyBlankExplicit", jar, "b123", "   ")

        when:
        task.verify()

        then:
        noExceptionThrown()
    }

    def "verify passes when explicit selected binding class is listed in SPI"() {
        given:
        def explicitBinding = "org.example.CustomBinding"
        def jar = createJar("spi-match.jar", [
                (explicitBinding.replace('.', '/') + ".class"): "",
                (callsiteClassPath("b123")): "",
                (SPI_PATH): "org.example.CustomBinding\n"
        ])
        def task = createTask("verifyExplicitMatch", jar, "b123", explicitBinding)

        when:
        task.verify()

        then:
        noExceptionThrown()
    }

    def "verify passes when selected default binding class exists once and SPI contains exactly that class"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def callsiteClass = callsiteClassPath("b123")
        def jar = createJar("ok.jar", [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClass): "",
                (SPI_PATH): "${bindingClass}\n"
        ])
        def task = createTask("verifyOk", jar, "b123", "")

        when:
        task.verify()

        then:
        noExceptionThrown()
    }

    def "verify fails with structured GradleException when selected binding class is missing"() {
        given:
        def archiveName = "verify-missing-selected-binding.jar"
        def archive = createJar(archiveName, [
                (callsiteClassPath("b123")): ""
        ])
        def task = createTask("verifyMissingSelectedBinding", archive, "b123", "")

        when:
        task.verify()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Packaged provider binding verification failed")
        ex.message.contains("archive=")
        ex.message.contains(archiveName)
        ex.message.contains("bindingId=b123")
        ex.message.contains("selectedBinding=${BindingClassSelector.defaultBindingClassName('b123')}")
        ex.message.contains("detail=Packaged binding class must be present")
    }

    def "verify fails with original detail message when binding class is absent from both flat and BOOT-INF classes layouts"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def callsiteClass = callsiteClassPath("b123")
        def archiveName = "verify-neither-layout-has-binding.jar"
        def archive = createJar(archiveName, [
                ("BOOT-INF/classes/" + callsiteClass): "",
                ("BOOT-INF/classes/" + SPI_PATH): "${bindingClass}\n"
        ])
        def task = createTask("verifyNeitherLayoutHasBinding", archive, "b123", "")

        when:
        task.verify()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Packaged provider binding verification failed")
        ex.message.contains("archive=")
        ex.message.contains(archiveName)
        ex.message.contains("bindingId=b123")
        ex.message.contains("selectedBinding=${BindingClassSelector.defaultBindingClassName('b123')}")
        ex.message.contains("detail=Packaged binding class must be present")
    }

    def "verify passes when archive nests all three entries under BOOT-INF classes prefix"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def callsiteClass = callsiteClassPath("b123")
        def jar = createJar("boot-inf-layout.jar", [
                ("BOOT-INF/classes/" + bindingClass.replace('.', '/') + ".class"): "",
                ("BOOT-INF/classes/" + callsiteClass): "",
                ("BOOT-INF/classes/" + SPI_PATH): "${bindingClass}\n"
        ])
        def task = createTask("verifyBootInfLayout", jar, "b123", "")

        when:
        task.verify()

        then:
        noExceptionThrown()
    }

    def "verify fails when runtime binding callsite class is missing"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def jarName = "missing-callsite.jar"
        def jar = createJar(jarName, [
                (bindingClass.replace('.', '/') + ".class"): "",
                (SPI_PATH): "${bindingClass}\n"
        ])
        def task = createTask("verifyMissingCallsite", jar, "b123", "")

        when:
        task.verify()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Packaged provider binding verification failed")
        ex.message.contains("archive=")
        ex.message.contains(jarName)
        ex.message.contains("bindingId=b123")
        ex.message.contains("selectedBinding=${BindingClassSelector.defaultBindingClassName('b123')}")
        ex.message.contains("runtime binding callsite class")
    }

    def "verify resolves callsite entry path through binding id sanitizer when raw binding id contains characters illegal as package segment"() {
        given:
        def rawBindingId = "my id"
        def sanitized = BindingIdSanitizer.sanitizeForPackageSegment(rawBindingId)
        assert sanitized != rawBindingId
        def bindingClass = BindingClassSelector.defaultBindingClassName(rawBindingId)
        def callsiteClass = callsiteClassPath(rawBindingId)
        assert callsiteClass.contains(sanitized)
        assert !callsiteClass.contains("my id")
        def jar = createJar("callsite-sanitized.jar", [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClass): "",
                (SPI_PATH): "${bindingClass}\n"
        ])
        def task = createTask("verifyCallsiteSanitized", jar, rawBindingId, "")

        when:
        task.verify()

        then:
        noExceptionThrown()
    }

    def "verify fails when SPI entry is missing"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def jarName = "spi-missing.jar"
        def jar = createJar(jarName, [
                (callsiteClassPath("b123")): "",
                (bindingClass.replace('.', '/') + ".class"): ""
        ])
        def task = createTask("verifyMissingSpi", jar, "b123", "")

        when:
        task.verify()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Packaged provider binding verification failed")
        ex.message.contains("archive=")
        ex.message.contains(jarName)
        ex.message.contains("bindingId=b123")
        ex.message.contains("selectedBinding=${BindingClassSelector.defaultBindingClassName('b123')}")
        ex.message.contains("SPI entry must be present")
    }

    def "verify passes when SPI file contains comments and blank lines around selected provider"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def callsiteClass = callsiteClassPath("b123")
        def jar = createJar("spi-with-comments-and-blanks.jar", [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClass): "",
                (SPI_PATH): "\n# provider list\n  ${bindingClass}   \n\n"
        ])
        def task = createTask("verifySpiWithCommentsAndBlanks", jar, "b123", "")

        when:
        task.verify()

        then:
        noExceptionThrown()
    }

    def "verify passes when SPI provider line contains inline comment suffix"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def callsiteClass = callsiteClassPath("b123")
        def jar = createJar("spi-inline-comment.jar", [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClass): "",
                (SPI_PATH): "${bindingClass} # generated by tool\n"
        ])
        def task = createTask("verifySpiInlineComment", jar, "b123", "")

        when:
        task.verify()

        then:
        noExceptionThrown()
    }

    def "verify passes when SPI entry size equals MAX_SPI_ENTRY_BYTES"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def callsiteClass = callsiteClassPath("b123")
        def maxBytes = 1024 * 1024
        def header = bindingClass + "\n"
        def padding = "\n" * (maxBytes - header.length())
        def spiContent = header + padding
        assert spiContent.getBytes("UTF-8").length == maxBytes
        def jar = createJar("spi-entry-at-max.jar", [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClass): "",
                (SPI_PATH): spiContent
        ])
        def task = createTask("verifySpiEntryAtMax", jar, "b123", "")

        when:
        task.verify()

        then:
        noExceptionThrown()
    }

    def "verify fails with structured GradleException when SPI entry size exceeds limit"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def callsiteClass = callsiteClassPath("b123")
        def archiveName = "verify-spi-oversize-entry.jar"
        def archive = createJar(archiveName, [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClass): "",
                (SPI_PATH): "x" * (1024 * 1024 + 64)
        ])
        def task = createTask("verifyOversizeSpiEntry", archive, "b123", "")

        when:
        task.verify()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Packaged provider binding verification failed")
        ex.message.contains("archive=")
        ex.message.contains(archiveName)
        ex.message.contains("bindingId=b123")
        ex.message.contains("selectedBinding=${BindingClassSelector.defaultBindingClassName('b123')}")
        ex.message.contains("detail=Packaged SPI entry is too large")
        ex.message.contains("max=" + (1024 * 1024))
    }

    def "verify wraps ResourceLimitExceededException as structured GradleException when SPI stream exceeds limit despite small declared size"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def callsiteClass = callsiteClassPath("b123")
        def archiveName = "verify-spi-stream-overflow.jar"
        def maxBytes = 1024 * 1024
        def actualPayload = "x" * (maxBytes + 64)
        def archive = createJar(archiveName, [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClass): "",
                (SPI_PATH): actualPayload
        ])
        forgeUncompressedSizeField(archive, SPI_PATH, maxBytes)
        def task = createTask("verifySpiStreamOverflow", archive, "b123", "")

        when:
        task.verify()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Packaged provider binding verification failed")
        ex.message.contains("archive=")
        ex.message.contains(archiveName)
        ex.message.contains("bindingId=b123")
        ex.message.contains("selectedBinding=${BindingClassSelector.defaultBindingClassName('b123')}")
        ex.message.contains("Packaged SPI entry exceeded max bytes while reading")
        ex.message.contains("max=" + maxBytes)
        !ex.message.contains("Packaged SPI entry is too large")
    }

    def "verify passes when SPI provider line length equals MAX_SPI_LINE_LENGTH"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def callsiteClass = callsiteClassPath("b123")
        def maxLineLength = 8192
        def paddedLine = bindingClass + (" " * (maxLineLength - bindingClass.length()))
        assert paddedLine.length() == maxLineLength
        def jar = createJar("spi-line-at-max.jar", [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClass): "",
                (SPI_PATH): paddedLine + "\n"
        ])
        def task = createTask("verifySpiLineAtMax", jar, "b123", "")

        when:
        task.verify()

        then:
        noExceptionThrown()
    }

    def "verify fails with structured GradleException when SPI provider line exceeds max length"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def callsiteClass = callsiteClassPath("b123")
        def archiveName = "verify-spi-oversize-line.jar"
        def archive = createJar(archiveName, [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClass): "",
                (SPI_PATH): ("a" * 9000) + "\n"
        ])
        def task = createTask("verifyOversizeSpiLine", archive, "b123", "")

        when:
        task.verify()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Packaged provider binding verification failed")
        ex.message.contains("archive=")
        ex.message.contains(archiveName)
        ex.message.contains("bindingId=b123")
        ex.message.contains("selectedBinding=${BindingClassSelector.defaultBindingClassName('b123')}")
        ex.message.contains("detail=Packaged SPI entry line is too long")
        ex.message.contains("max=8192")
    }

    def "verify rethrows non-ResourceLimitExceededException IOException from SPI read without wrapping in GradleException"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def callsiteClass = callsiteClassPath("b123")
        def archiveName = "verify-corrupt-spi-deflate.jar"
        def jar = createJar(archiveName, [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClass): "",
                (SPI_PATH): "${bindingClass}\n"
        ])
        corruptZipEntryPayload(jar, SPI_PATH)
        def task = createTask("verifyCorruptSpiDeflate", jar, "b123", "")

        when:
        task.verify()

        then:
        def ex = thrown(IOException)
        !(ex instanceof GradleException)
        !(ex instanceof ResourceLimitExceededException)
        ex.message == null || !ex.message.contains("exceeded max bytes while reading")
        ex.message == null || !ex.message.contains("Packaged provider binding verification failed")
    }

    def "verify fails when SPI contains multiple providers"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def jarName = "spi-multi-provider.jar"
        def jar = createJar(jarName, [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClassPath("b123")): "",
                (SPI_PATH): "${bindingClass}\norg.example.OtherBinding\n"
        ])
        def task = createTask("verifyMultiProvider", jar, "b123", "")

        when:
        task.verify()

        then:
        def ex = thrown(GradleException)
        ex.message.contains(jarName)
        ex.message.contains("bindingId=b123")
        ex.message.contains("must contain exactly one entry")
        ex.message.contains("but found 2")
        ex.message.contains("providers=[${bindingClass}, org.example.OtherBinding]")
    }

    def "verify fails with structured GradleException when SPI file contains only comments and blank lines yielding zero providers"() {
        given:
        def bindingClass = BindingClassSelector.defaultBindingClassName("b123")
        def jarName = "spi-zero-providers.jar"
        def jar = createJar(jarName, [
                (bindingClass.replace('.', '/') + ".class"): "",
                (callsiteClassPath("b123")): "",
                (SPI_PATH): "# only a comment\n\n# another\n   \n"
        ])
        def task = createTask("verifyZeroProviders", jar, "b123", "")

        when:
        task.verify()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Packaged provider binding verification failed")
        ex.message.contains(jarName)
        ex.message.contains("bindingId=b123")
        ex.message.contains("selectedBinding=${BindingClassSelector.defaultBindingClassName('b123')}")
        ex.message.contains("must contain exactly one entry")
        ex.message.contains("but found 0")
        ex.message.contains("providers=[]")
    }

    def "verify fails when the single SPI provider does not equal the explicit selected binding class"() {
        given:
        def explicitBinding = "org.example.CustomBinding"
        def jarName = "spi-mismatch.jar"
        def jar = createJar(jarName, [
                (explicitBinding.replace('.', '/') + ".class"): "",
                (callsiteClassPath("b123")): "",
                (SPI_PATH): "org.example.OtherBinding\n"
        ])
        def task = createTask("verifyExplicitMismatch", jar, "b123", explicitBinding)

        when:
        task.verify()

        then:
        def ex = thrown(GradleException)
        ex.message.contains(jarName)
        ex.message.contains("bindingId=b123")
        ex.message.contains(explicitBinding)
        ex.message.contains("must equal selected binding class")
        ex.message.contains("providers=[org.example.OtherBinding]")
    }

    private static String callsiteClassPath(String bindingId) {
        def sanitized = BindingIdSanitizer.sanitizeForPackageSegment(bindingId)
        return (PrunusPluginConstants.GENERATED_AOT_PACKAGE
                + "."
                + sanitized
                + "."
                + PrunusPluginConstants.GENERATED_AOT_RUNTIME_CALLSITE_SIMPLE_NAME).replace('.', '/') + ".class"
    }

    private VerifyPackagedProviderBindingTask createTask(String name, File archiveFile, String bindingId, String explicitBinding) {
        def project = ProjectBuilder.builder().withName(name).build()
        project.tasks.register(name, VerifyPackagedProviderBindingTask) { t ->
            t.archiveFile.set(archiveFile)
            t.bindingId.set(bindingId)
            t.explicitBindingClass.set(explicitBinding)
        }.get() as VerifyPackagedProviderBindingTask
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

    private static void corruptZipEntryPayload(File jar, String entryName) {
        byte[] bytes = jar.bytes
        byte[] pathBytes = entryName.getBytes("UTF-8")
        int pathIdx = -1
        for (int i = 0; i <= bytes.length - pathBytes.length; i++) {
            boolean match = true
            for (int j = 0; j < pathBytes.length; j++) {
                if (bytes[i + j] != pathBytes[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                pathIdx = i
                break
            }
        }
        assert pathIdx >= 0
        int payloadStart = pathIdx + pathBytes.length
        int payloadEnd = Math.min(payloadStart + 32, bytes.length)
        for (int i = payloadStart; i < payloadEnd; i++) {
            bytes[i] = (byte) 0xFF
        }
        jar.bytes = bytes
    }

    private static void forgeUncompressedSizeField(File jar, String entryName, long forgedSize) {
        byte[] bytes = jar.bytes
        byte[] pathBytes = entryName.getBytes("UTF-8")
        byte[] forgedLeBytes = [
                (byte) (forgedSize & 0xFF),
                (byte) ((forgedSize >> 8) & 0xFF),
                (byte) ((forgedSize >> 16) & 0xFF),
                (byte) ((forgedSize >> 24) & 0xFF)
        ] as byte[]
        int patchedLocations = 0
        for (int i = 0; i <= bytes.length - pathBytes.length; i++) {
            boolean match = true
            for (int j = 0; j < pathBytes.length; j++) {
                if (bytes[i + j] != pathBytes[j]) {
                    match = false
                    break
                }
            }
            if (!match) {
                continue
            }
            int headerStart = locateContainingHeader(bytes, i)
            if (headerStart < 0) {
                continue
            }
            int uncompressedSizeOffset = headerOffsetOfUncompressedSize(bytes, headerStart)
            if (uncompressedSizeOffset < 0) {
                continue
            }
            for (int k = 0; k < 4; k++) {
                bytes[uncompressedSizeOffset + k] = forgedLeBytes[k]
            }
            patchedLocations++
        }
        assert patchedLocations >= 2
        jar.bytes = bytes
    }

    private static int locateContainingHeader(byte[] bytes, int nameOffset) {
        int localStart = nameOffset - 30
        if (localStart >= 0
                && bytes[localStart] == (byte) 0x50
                && bytes[localStart + 1] == (byte) 0x4B
                && bytes[localStart + 2] == (byte) 0x03
                && bytes[localStart + 3] == (byte) 0x04) {
            return localStart
        }
        int centralStart = nameOffset - 46
        if (centralStart >= 0
                && bytes[centralStart] == (byte) 0x50
                && bytes[centralStart + 1] == (byte) 0x4B
                && bytes[centralStart + 2] == (byte) 0x01
                && bytes[centralStart + 3] == (byte) 0x02) {
            return centralStart
        }
        return -1
    }

    private static int headerOffsetOfUncompressedSize(byte[] bytes, int headerStart) {
        byte third = bytes[headerStart + 2]
        if (third == (byte) 0x03) {
            return headerStart + 22
        }
        if (third == (byte) 0x01) {
            return headerStart + 24
        }
        return -1
    }

}
