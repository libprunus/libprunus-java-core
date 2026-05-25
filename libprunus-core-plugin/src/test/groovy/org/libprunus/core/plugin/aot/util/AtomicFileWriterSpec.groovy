package org.libprunus.core.plugin.aot.util

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import spock.lang.Specification
import spock.lang.TempDir

class AtomicFileWriterSpec extends Specification {

    @TempDir
    Path tempDir

    def "private constructor throws UnsupportedOperationException on instantiation"() {
        when:
        new AtomicFileWriter()

        then:
        thrown(UnsupportedOperationException)
    }

    def "writeIfChanged writes content to a new file when target does not exist"() {
        given:
        def target = tempDir.resolve("output.bin")
        def content = [1, 2, 3] as byte[]

        when:
        AtomicFileWriter.writeIfChanged(target, content)

        then:
        Files.readAllBytes(target) == content
    }

    def "writeIfChanged creates nested parent directories when they do not exist"() {
        given:
        def target = tempDir.resolve("a/b/c/output.bin")
        def content = [42] as byte[]

        when:
        AtomicFileWriter.writeIfChanged(target, content)

        then:
        Files.isDirectory(tempDir.resolve("a/b/c"))
        Files.readAllBytes(target) == content
    }

    def "writeIfChanged replaces existing file content with new bytes"() {
        given:
        def target = tempDir.resolve("output.bin")
        Files.write(target, [1, 2, 3] as byte[])

        when:
        AtomicFileWriter.writeIfChanged(target, [4, 5, 6] as byte[])

        then:
        Files.readAllBytes(target) == [4, 5, 6] as byte[]
    }

    def "writeIfChanged does not modify mtime nor create tmp file when bytes unchanged"() {
        given:
        def target = tempDir.resolve("payload.bin")
        def content = "abc".getBytes()
        AtomicFileWriter.writeIfChanged(target, content)
        def pastMtime = FileTime.fromMillis(0L)
        Files.setLastModifiedTime(target, pastMtime)

        when:
        AtomicFileWriter.writeIfChanged(target, content)

        then:
        Files.getLastModifiedTime(target) == pastMtime
        Files.list(target.parent).withCloseable { stream ->
            stream.collect().findAll { it.fileName.toString().endsWith(".tmp") }
        }.isEmpty()
    }

    def "writeIfChanged leaves no temp file residue in target parent directory after success"() {
        given:
        def target = tempDir.resolve("output.bin")
        def content = [1, 2, 3] as byte[]

        when:
        AtomicFileWriter.writeIfChanged(target, content)

        then:
        Files.readAllBytes(target) == content
        Files.list(target.parent).withCloseable { stream ->
            stream.collect().findAll { it.fileName.toString().endsWith(".tmp") }
        }.isEmpty()
    }

    def "writeIfChanged throws IOException without leaving tmp residue or modifying target when move cannot replace a non-empty directory"() {
        given:
        def target = tempDir.resolve("output")
        Files.createDirectory(target)
        def occupant = target.resolve("blocker")
        Files.write(occupant, [7, 7, 7] as byte[])

        when:
        AtomicFileWriter.writeIfChanged(target, [1, 2, 3] as byte[])

        then:
        thrown(IOException)
        Files.isDirectory(target)
        Files.readAllBytes(occupant) == [7, 7, 7] as byte[]
        Files.list(target.parent).withCloseable { stream ->
            stream.collect().findAll { it.fileName.toString().endsWith(".tmp") }
        }.isEmpty()
    }

    def "writeIfChanged String overload encodes content with the supplied charset"() {
        given:
        def target = tempDir.resolve("text.txt")
        def content = "αβ"

        when:
        AtomicFileWriter.writeIfChanged(target, content, StandardCharsets.UTF_16BE)

        then:
        Files.readAllBytes(target) == content.getBytes(StandardCharsets.UTF_16BE)
        Files.readAllBytes(target) != content.getBytes(StandardCharsets.UTF_8)
    }

    def "writeIfChanged String overload skips rewrite when encoded bytes match existing content"() {
        given:
        def target = tempDir.resolve("text.txt")
        AtomicFileWriter.writeIfChanged(target, "stable", StandardCharsets.UTF_8)
        def pastMtime = FileTime.fromMillis(0L)
        Files.setLastModifiedTime(target, pastMtime)

        when:
        AtomicFileWriter.writeIfChanged(target, "stable", StandardCharsets.UTF_8)

        then:
        Files.getLastModifiedTime(target) == pastMtime
        Files.list(target.parent).withCloseable { stream ->
            stream.collect().findAll { it.fileName.toString().endsWith(".tmp") }
        }.isEmpty()
    }

    def "writeIfChanged String overload does not short-circuit when a different charset would produce different bytes"() {
        given:
        def target = tempDir.resolve("text.txt")
        def content = "αβ"
        AtomicFileWriter.writeIfChanged(target, content.getBytes(StandardCharsets.UTF_8))
        def pastMtime = FileTime.fromMillis(0L)
        Files.setLastModifiedTime(target, pastMtime)

        when:
        AtomicFileWriter.writeIfChanged(target, content, StandardCharsets.UTF_16BE)

        then:
        Files.getLastModifiedTime(target) != pastMtime
        Files.readAllBytes(target) == content.getBytes(StandardCharsets.UTF_16BE)
    }
}
