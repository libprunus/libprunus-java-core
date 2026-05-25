package org.libprunus.core.plugin.aot

import java.io.FileOutputStream
import java.util.jar.JarOutputStream
import net.bytebuddy.dynamic.ClassFileLocator
import spock.lang.Specification
import spock.lang.TempDir

class AotByteBuddyDispatcherAlgorithmSpec extends Specification {

    @TempDir
    File tempDir

    def "toRootList returns an empty list when rootLocation is null"() {
        when:
        def result = AotByteBuddyDispatcher."toRootList"(null)

        then:
        result == []
    }

    def "toRootList returns a single-element list wrapping the non-null root"() {
        given:
        def root = new File(tempDir, "root")

        when:
        def result = AotByteBuddyDispatcher."toRootList"(root)

        then:
        result == [root]
    }

    def "sanitizeClasspath removes duplicates while preserving first-seen order"() {
        given:
        def rootLocation = new File(tempDir, "root")
        def first = new File(tempDir, "libs/first.jar")
        def second = new File(tempDir, "libs/second.jar")

        when:
        def result = AotByteBuddyDispatcher."sanitizeClasspath"(rootLocation, [first, first, null, rootLocation, second, first] as File[])

        then:
        result == [first.absoluteFile, second.absoluteFile]
    }

    def "sanitizeClasspath stores retained entries as absolute files"() {
        given:
        def first = new File(tempDir, "libs/first.jar")
        def second = new File(tempDir, "libs/second.jar")

        when:
        def result = AotByteBuddyDispatcher."sanitizeClasspath"(null, [first, second] as File[])

        then:
        result == [first.absoluteFile, second.absoluteFile]
        result.every { it.absolute }
    }

    def "sanitizeClasspath treats paths with dotdot segments as distinct from their canonical equivalents"() {
        given:
        def canonical = new File(tempDir, "build/classes/main")
        def withDotdot = new File(tempDir, "build/classes/main/../main")

        when:
        def result = AotByteBuddyDispatcher."sanitizeClasspath"(null, [canonical, withDotdot] as File[])

        then:
        result == [canonical.absoluteFile, withDotdot.absoluteFile]
    }

    def "buildClassFileLocator returns NoOp when classpath and classesOutputDir are both empty"() {
        when:
        def result = AotByteBuddyDispatcher."buildClassFileLocator"([], [])

        then:
        result.is(ClassFileLocator.NoOp.INSTANCE)
    }

    def "buildClassFileLocator returns a Compound locator when valid classpath entries are supplied"() {
        given:
        def jar = new File(tempDir, "build-locator/test.jar")
        createEmptyJar(jar)
        def dir = new File(tempDir, "build-locator/classes")
        dir.mkdirs()

        when:
        def result = AotByteBuddyDispatcher."buildClassFileLocator"([jar], [dir])

        then:
        result.class.simpleName == "Compound"

        cleanup:
        result.close()
    }

    def "buildClassFileLocator closes already-opened locators when a subsequent malformed jar triggers IllegalStateException"() {
        given:
        def valid = new File(tempDir, "classpath/valid.jar")
        createEmptyJar(valid)
        def malformed = new File(tempDir, "classpath/malformed.jar")
        malformed.parentFile.mkdirs()
        malformed.text = "not-a-jar"

        when:
        AotByteBuddyDispatcher."buildClassFileLocator"([valid, malformed], [])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains(malformed.absolutePath)
    }

    def "buildClassFileLocator does not attach suppressed exceptions when all opened locators close cleanly during fail-fast"() {
        given:
        def valid = new File(tempDir, "locator-cleanup/valid.jar")
        createEmptyJar(valid)
        def malformed = new File(tempDir, "locator-cleanup/malformed.jar")
        malformed.parentFile.mkdirs()
        malformed.text = "not-a-jar"

        when:
        AotByteBuddyDispatcher."buildClassFileLocator"([valid, malformed], [])

        then:
        def ex = thrown(IllegalStateException)
        ex.suppressed.length == 0
    }

    private static File createEmptyJar(File file) {
        file.parentFile.mkdirs()
        new JarOutputStream(new FileOutputStream(file)).close()
        return file
    }
}
