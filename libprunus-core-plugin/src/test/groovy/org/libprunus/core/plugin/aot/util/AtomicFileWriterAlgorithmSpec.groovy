package org.libprunus.core.plugin.aot.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import spock.lang.Specification
import spock.lang.TempDir

class AtomicFileWriterAlgorithmSpec extends Specification {

    @TempDir
    Path tempDir

    def "writeIfChanged skips write exclusively when size and byte content both match"() {
        given:
        def target = tempDir.resolve("output.bin")
        Files.write(target, existingContent as byte[])
        def staleTime = FileTime.fromMillis(Files.getLastModifiedTime(target).toMillis() - 5000)
        Files.setLastModifiedTime(target, staleTime)

        when:
        AtomicFileWriter.writeIfChanged(target, incomingContent as byte[])

        then:
        def mtimeChanged = Files.getLastModifiedTime(target) != staleTime
        mtimeChanged == expectsWrite

        where:
        existingContent | incomingContent | expectsWrite
        [1, 2, 3]       | [1, 2, 3]       | false
        []              | []              | false
        [1, 2, 3]       | [4, 5, 6]       | true
        [1, 2, 3]       | [1, 2, 4]       | true
        [1, 2, 3]       | [1, 2, 3, 4]    | true
        [1, 2, 3]       | [1, 2]          | true
        [1, 2, 3]       | []              | true
        []              | [0]             | true
    }

    def "writeIfChanged writes payloads byte-for-byte regardless of size"() {
        given:
        def target = tempDir.resolve("output.bin")

        when:
        AtomicFileWriter.writeIfChanged(target, content as byte[])

        then:
        Files.readAllBytes(target) == content as byte[]

        where:
        content << [
            (0..255).collect { it as byte },
            (1..1024).collect { (it % 256) as byte }
        ]
    }
}
