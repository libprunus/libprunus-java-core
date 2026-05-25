package org.libprunus.core.plugin.aot.task

import java.nio.file.Paths
import spock.lang.Specification
import spock.lang.TempDir

class PortablePathOrderSpec extends Specification {

    @TempDir
    File tempDir

    def "sortByProjectRelativePath orders project-internal files ascending by portable relative path"() {
        given:
        def projectDir = tempDir.toPath()
        def fileZA = new File(tempDir, "z/a.class")
        fileZA.parentFile.mkdirs()
        fileZA.createNewFile()
        def fileAZ = new File(tempDir, "a/z.class")
        fileAZ.parentFile.mkdirs()
        fileAZ.createNewFile()
        def fileAA = new File(tempDir, "a/a.class")
        fileAA.parentFile.mkdirs()
        fileAA.createNewFile()

        when:
        def sorted = PortablePathOrder.sortByProjectRelativePath([fileZA, fileAZ, fileAA], projectDir)

        then:
        sorted == [fileAA, fileAZ, fileZA]
    }

    def "sortByProjectRelativePath places external files after project-internal files regardless of insertion order"() {
        given:
        def externalRoot = new File(tempDir.parentFile, "external-${System.nanoTime()}")
        def external = new File(externalRoot, "x.class")
        external.parentFile.mkdirs()
        external.createNewFile()
        def internal = new File(tempDir, "zzz/last.class")
        internal.parentFile.mkdirs()
        internal.createNewFile()

        when:
        def sorted = PortablePathOrder.sortByProjectRelativePath([external, internal], tempDir.toPath())

        then:
        sorted == [internal, external]
    }

    def "sortByProjectRelativePath uses full absolute portable path as secondary key when tail segments tie"() {
        given:
        def rootA = new File(tempDir.parentFile, "aa-${System.nanoTime()}")
        def rootB = new File(tempDir.parentFile, "bb-${System.nanoTime()}")
        def fileA = new File(rootA, "p1/p2/p3/p4/p5/p6/p7/p8/c.class")
        fileA.parentFile.mkdirs()
        fileA.createNewFile()
        def fileB = new File(rootB, "p1/p2/p3/p4/p5/p6/p7/p8/c.class")
        fileB.parentFile.mkdirs()
        fileB.createNewFile()

        when:
        def sorted = PortablePathOrder.sortByProjectRelativePath([fileB, fileA], tempDir.toPath())

        then:
        sorted == [fileA, fileB]
    }

    def "sortByProjectRelativePath returns empty list when input is empty"() {
        when:
        def sorted = PortablePathOrder.sortByProjectRelativePath([], tempDir.toPath())

        then:
        sorted == []
    }

    def "sortByProjectRelativePath classifies files with unnormalized dot segments as project-internal"() {
        given:
        def real = new File(tempDir, "a/b/c.class")
        real.parentFile.mkdirs()
        real.createNewFile()
        def aliased = new File(tempDir, "a/./b/../b/c.class")
        def external = new File(tempDir.parentFile, "outside-${System.nanoTime()}/c.class")
        external.parentFile.mkdirs()
        external.createNewFile()

        when:
        def sorted = PortablePathOrder.sortByProjectRelativePath([external, aliased], tempDir.toPath())

        then:
        sorted == [aliased, external]
    }

    def "sortByPortableTailPath orders entries by file name then portable tail then absolute portable path"() {
        given:
        def fileA = new File(tempDir, "lib-a/META-INF/services/x.txt")
        fileA.parentFile.mkdirs()
        fileA.createNewFile()
        def fileB = new File(tempDir, "lib-b/META-INF/services/x.txt")
        fileB.parentFile.mkdirs()
        fileB.createNewFile()
        def fileC = new File(tempDir, "lib-c/META-INF/services/y.txt")
        fileC.parentFile.mkdirs()
        fileC.createNewFile()

        when:
        def sorted = PortablePathOrder.sortByPortableTailPath([fileC, fileB, fileA])

        then:
        sorted == [fileA, fileB, fileC]
    }

    def "sortByPortableTailPath uses full absolute portable path as secondary key when file name and portable tail both tie"() {
        given:
        def rootA = new File(tempDir.parentFile, "tail-tie-aa-${System.nanoTime()}")
        def rootB = new File(tempDir.parentFile, "tail-tie-bb-${System.nanoTime()}")
        def fileA = new File(rootA, "p1/p2/p3/p4/p5/p6/p7/p8/shared.txt")
        fileA.parentFile.mkdirs()
        fileA.createNewFile()
        def fileB = new File(rootB, "p1/p2/p3/p4/p5/p6/p7/p8/shared.txt")
        fileB.parentFile.mkdirs()
        fileB.createNewFile()

        when:
        def sorted = PortablePathOrder.sortByPortableTailPath([fileB, fileA])

        then:
        sorted == [fileA, fileB]
    }

    def "sortByPortableTailPath returns empty list when input is empty"() {
        when:
        def sorted = PortablePathOrder.sortByPortableTailPath([])

        then:
        sorted == []
    }

    def "portableTail returns last N segments joined by slash respecting boundary segment counts"() {
        expect:
        PortablePathOrder.portableTail(Paths.get(pathStr), segmentCount) == expected

        where:
        pathStr                                 | segmentCount || expected
        "/"                                     | 8            || ""
        "/a/b/c"                                | 8            || "a/b/c"
        "/a/b/c/d/e/f/g/h"                      | 8            || "a/b/c/d/e/f/g/h"
        "/x/y/a/b/c/d/e/f/g/h"                  | 8            || "a/b/c/d/e/f/g/h"
        "/a/b/c"                                | 1            || "c"
    }
}
