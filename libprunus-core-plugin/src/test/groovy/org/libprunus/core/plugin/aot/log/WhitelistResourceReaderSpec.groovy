package org.libprunus.core.plugin.aot.log

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.ZipException
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir

class WhitelistResourceReaderSpec extends Specification {

    @TempDir
    File tempDir

    def "private constructor throws UnsupportedOperationException to prevent instantiation"() {
        when:
        new WhitelistResourceReader()

        then:
        thrown(UnsupportedOperationException)
    }

    def "readFrom skips non-existent classpath entry without populating target"() {
        given:
        def absent = new File("/this/does/not/exist/fake.jar")
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readFrom(absent, target)

        then:
        noExceptionThrown()
        target.isEmpty()
    }

    def "readFrom reads whitelist resource file from directory entry"() {
        given:
        def cpDir = new File(tempDir, "cp")
        def whitelistPath = new File(cpDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        whitelistPath.text = "java.lang.String\njava.util.List\n"
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readFrom(cpDir, target)

        then:
        noExceptionThrown()
        target == ["java.lang.String", "java.util.List"] as LinkedHashSet
    }

    def "readFrom leaves target untouched when directory entry has no whitelist resource file"() {
        given:
        def cpDir = new File(tempDir, "empty-cp")
        cpDir.mkdirs()
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readFrom(cpDir, target)

        then:
        noExceptionThrown()
        target.isEmpty()
    }

    def "readFrom reads whitelist resource entry from jar file"() {
        given:
        def jarFile = new File(tempDir, "lib.jar")
        jarFile.withOutputStream { os ->
            def jos = new java.util.jar.JarOutputStream(os)
            jos.putNextEntry(new java.util.jar.JarEntry(PrunusPluginConstants.WHITELIST_RESOURCE_PATH))
            jos.write("java.lang.Number\n".bytes)
            jos.closeEntry()
            jos.close()
        }
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readFrom(jarFile, target)

        then:
        noExceptionThrown()
        target == ["java.lang.Number"] as LinkedHashSet
    }

    def "readFrom leaves target untouched when jar file does not contain whitelist resource entry"() {
        given:
        def jarFile = new File(tempDir, "no-whitelist.jar")
        jarFile.withOutputStream { os ->
            def jos = new java.util.jar.JarOutputStream(os)
            jos.putNextEntry(new java.util.jar.JarEntry("foo/bar.txt"))
            jos.write("not the whitelist".bytes)
            jos.closeEntry()
            jos.close()
        }
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readFrom(jarFile, target)

        then:
        noExceptionThrown()
        target.isEmpty()
    }

    def "readFrom ignores classpath entry that is neither a directory nor a jar file"() {
        given:
        def txtFile = new File(tempDir, "something.txt")
        txtFile.text = "this is not a jar nor a directory"
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readFrom(txtFile, target)

        then:
        noExceptionThrown()
        target.isEmpty()
    }

    def "readWhitelistFile wraps Files.size NoSuchFileException with 'Failed to inspect whitelist file' diagnostic"() {
        given:
        def stalePath = tempDir.toPath().resolve("transient.txt")
        Files.writeString(stalePath, "java.lang.String\n")
        Files.delete(stalePath)
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readWhitelistFile(stalePath, target)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Failed to inspect whitelist file: " + stalePath
        ex.cause instanceof NoSuchFileException
        target.isEmpty()
    }

    def "readWhitelistFile wraps read IOException with 'Failed to read whitelist file' diagnostic"() {
        given:
        def cpDir = new File(tempDir, "dir-as-file")
        def whitelistPath = new File(cpDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        Files.createDirectory(whitelistPath.toPath())
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readWhitelistFile(whitelistPath.toPath(), target)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Failed to read whitelist file: " + whitelistPath.toPath()
        ex.cause instanceof IOException
        !(ex.cause instanceof NoSuchFileException)
        target.isEmpty()
    }

    def "readWhitelistFromJar tolerates corrupt or non-ZIP jar by leaving target unchanged and not throwing"() {
        given:
        def corruptJar = new File(tempDir, "corrupt.jar")
        corruptJar.text = "this is not a zip file"
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readFrom(corruptJar, target)

        then:
        noExceptionThrown()
        target.isEmpty()
    }

    @IgnoreIf({ !Files.getFileStore(java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"))).supportsFileAttributeView("posix") })
    def "readWhitelistFromJar wraps non-ZipException IOException with 'Failed to read whitelist from jar' diagnostic"() {
        given:
        def lockedJar = new File(tempDir, "locked.jar")
        lockedJar.withOutputStream { os ->
            def jos = new java.util.jar.JarOutputStream(os)
            jos.putNextEntry(new java.util.jar.JarEntry("placeholder.txt"))
            jos.write("payload".bytes)
            jos.closeEntry()
            jos.close()
        }
        Files.setPosixFilePermissions(lockedJar.toPath(), PosixFilePermissions.fromString("---------"))
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readFrom(lockedJar, target)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Failed to read whitelist from jar: " + lockedJar
        ex.cause instanceof IOException
        !(ex.cause instanceof ZipException)
        target.isEmpty()

        cleanup:
        if (lockedJar.exists()) {
            Files.setPosixFilePermissions(lockedJar.toPath(), PosixFilePermissions.fromString("rw-------"))
        }
    }

    def "readBoundedWhitelist rejects whitelist resource whose pre-checked size exceeds MAX_WHITELIST_RESOURCE_BYTES"() {
        given:
        def cpDir = new File(tempDir, "oversize")
        def whitelistPath = new File(cpDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        def bytes = new byte[(WhitelistResourceReader.MAX_WHITELIST_RESOURCE_BYTES as int) + 1]
        Arrays.fill(bytes, (byte) 'a')
        whitelistPath.bytes = bytes
        def actualSize = Files.size(whitelistPath.toPath())
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readFrom(cpDir, target)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Whitelist resource is too large: source=" + whitelistPath.toPath().toString() +
                ", size=" + actualSize +
                ", max=" + WhitelistResourceReader.MAX_WHITELIST_RESOURCE_BYTES
        ex.cause == null
        target.isEmpty()
    }

    def "readBoundedWhitelist rejects whitelist line longer than MAX_WHITELIST_LINE_LENGTH with full diagnostic"() {
        given:
        def cpDir = new File(tempDir, "longline")
        def whitelistPath = new File(cpDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        whitelistPath.text = ("a" * 9000) + "\n"
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readFrom(cpDir, target)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Whitelist resource line too long: source=" + whitelistPath.toPath().toString() +
                ", lineLength=9000" +
                ", max=" + WhitelistResourceReader.MAX_WHITELIST_LINE_LENGTH
        target.isEmpty()
    }

    def "readLines strips UTF-8 BOM only from first line and treats subsequent BOM as part of class name"() {
        given:
        def firstBomDir = new File(tempDir, "bom-first")
        def firstBomPath = new File(firstBomDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        firstBomPath.parentFile.mkdirs()
        firstBomPath.bytes = "\uFEFFjava.lang.String\njava.lang.Number\n".getBytes("UTF-8")
        def firstTarget = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readFrom(firstBomDir, firstTarget)

        then:
        noExceptionThrown()
        firstTarget == ["java.lang.String", "java.lang.Number"] as LinkedHashSet

        when:
        def secondBomDir = new File(tempDir, "bom-second")
        def secondBomPath = new File(secondBomDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        secondBomPath.parentFile.mkdirs()
        secondBomPath.bytes = "java.lang.String\n\uFEFFjava.lang.Number\n".getBytes("UTF-8")
        def secondTarget = new LinkedHashSet<String>()
        WhitelistResourceReader.readFrom(secondBomDir, secondTarget)

        then:
        secondTarget.contains("java.lang.String")
        secondTarget.contains("\uFEFFjava.lang.Number")
        !secondTarget.contains("java.lang.Number")
    }

    def "readLines skips comment lines and whitespace-only lines"() {
        given:
        def cpDir = new File(tempDir, "comments")
        def whitelistPath = new File(cpDir, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistPath.parentFile.mkdirs()
        whitelistPath.text = "# comment\n\njava.lang.String\n  \njava.lang.Number\n"
        def target = new LinkedHashSet<String>()

        when:
        WhitelistResourceReader.readFrom(cpDir, target)

        then:
        noExceptionThrown()
        target == ["java.lang.String", "java.lang.Number"] as LinkedHashSet
    }
}
