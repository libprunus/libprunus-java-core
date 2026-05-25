package org.libprunus.core.plugin.aot

import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarOutputStream
import net.bytebuddy.dynamic.ClassFileLocator
import spock.lang.Specification
import spock.lang.TempDir

class AotClassFileLocatorFactorySpec extends Specification {

    @TempDir
    File tempDir

    def "create either fails before traversal or succeeds cleanly for future Java major versions"() {
        given:
        def dirA = new File(tempDir, "classes-future")
        dirA.mkdirs()
        def nextCalls = new AtomicInteger(0)
        def dirs = iterableWithCounter([dirA], nextCalls)

        when:
        def thrownError = null
        try {
            AotClassFileLocatorFactory.create(dirs, [], targetCompatibility)
        } catch (RuntimeException ex) {
            thrownError = ex
        }

        then:
        if (thrownError != null) {
            assert nextCalls.get() == 0
        } else {
            assert nextCalls.get() == 1
        }

        where:
        targetCompatibility << ["99", "999"]
    }

    // WHY: failure occurs at iterator() bootstrap before any traversal begins;
    // create() has no partial-state surface (no shared collection, no side effects),
    // so there is no symmetric success-path observable to assert.
    def "create propagates iterable bootstrap failure from iterator()"() {
        given:
        def dirs = iterableThatFailsOnIterator("File tree is locked")

        when:
        AotClassFileLocatorFactory.create(dirs, [], "11")

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("File tree is locked")
    }

    def "create propagates runtime failures during traversal and stops processing"() {
        given:
        def first = new File(tempDir, "classes-first")
        first.mkdirs()
        def nextCalls = new AtomicInteger(0)
        def dirs = iterableThatFailsOnSecondNext(first, error, nextCalls)

        when:
        AotClassFileLocatorFactory.create(dirs, [], "11")

        then:
        def ex = thrown(expectedException)
        ex.is(error)
        nextCalls.get() == 2

        where:
        error                                                    || expectedException
        new IllegalStateException("simulated iteration failure") || IllegalStateException
        new SecurityException("Sandbox access denied")           || SecurityException
        new ConcurrentModificationException()                    || ConcurrentModificationException
    }

    def "create returns a Compound chain ordered as classes-folder, runtime-jar, bootloader for the public 3-arg API"() {
        given:
        def classesDir = new File(tempDir, "compose-happy/classes")
        classesDir.mkdirs()
        def jar = new File(tempDir, "compose-happy/runtime.jar")
        createEmptyJar(jar)

        when:
        def result = AotClassFileLocatorFactory.create([classesDir], [jar], "17")

        then:
        result instanceof ClassFileLocator.Compound
        def locators = extractCompoundLocators((ClassFileLocator.Compound) result)
        locators.size() == 3
        locators[0].class.simpleName.contains("ForFolder")
        locators[1].class.simpleName.contains("ForJarFile")
        locators[2].is(AotClassFileLocatorFactory.BOOT_LOADER_LOCATOR)
        AotClassFileLocatorFactory.BOOT_LOADER_LOCATOR.locate("java/lang/Object").isResolved()

        cleanup:
        result.close()
    }

    def "compose includes BOOT_LOADER_LOCATOR as last element"() {
        given:
        def classesLocator = ClassFileLocator.NoOp.INSTANCE
        def jar = new File(tempDir, "compose-boot/valid.jar")
        createEmptyJar(jar)

        when:
        def result = AotClassFileLocatorFactory.compose(classesLocator, [jar])

        then:
        def locators = extractCompoundLocators((ClassFileLocator.Compound) result)
        locators.last().is(AotClassFileLocatorFactory.BOOT_LOADER_LOCATOR)

        cleanup:
        result.close()
    }

    def "compose reuses the BOOT_LOADER_LOCATOR singleton instance across independent invocations"() {
        given:
        def classesDirA = new File(tempDir, "compose-singleton/classesA")
        classesDirA.mkdirs()
        def classesDirB = new File(tempDir, "compose-singleton/classesB")
        classesDirB.mkdirs()
        def folderA = new ClassFileLocator.ForFolder(classesDirA)
        def folderB = new ClassFileLocator.ForFolder(classesDirB)

        when:
        def first = AotClassFileLocatorFactory.compose(folderA, [])
        def second = AotClassFileLocatorFactory.compose(folderB, [])

        then:
        def firstInner = extractCompoundLocators((ClassFileLocator.Compound) first)
        def secondInner = extractCompoundLocators((ClassFileLocator.Compound) second)
        firstInner.last().is(AotClassFileLocatorFactory.BOOT_LOADER_LOCATOR)
        secondInner.last().is(AotClassFileLocatorFactory.BOOT_LOADER_LOCATOR)
        firstInner.last().is(secondInner.last())
        !firstInner[0].is(secondInner[0])

        cleanup:
        first.close()
        second.close()
    }

    def "compose silently skips null and non-existent runtimeClasspath entries"() {
        given:
        def classesDir = new File(tempDir, "compose-skip/classes")
        classesDir.mkdirs()
        def classesLocator = new ClassFileLocator.ForFolder(classesDir)
        def validJar = new File(tempDir, "compose-skip/valid.jar")
        createEmptyJar(validJar)
        def absent = new File(tempDir, "compose-skip/absent.jar")

        when:
        def result = AotClassFileLocatorFactory.compose(classesLocator, [validJar, null, absent])

        then:
        def locators = extractCompoundLocators((ClassFileLocator.Compound) result)
        locators.size() == 3
        locators[0].is(classesLocator)
        locators[1].class.simpleName.contains("ForJarFile")
        locators[2].is(AotClassFileLocatorFactory.BOOT_LOADER_LOCATOR)

        cleanup:
        result.close()
    }

    def "compose closes all opened locators except bootloader when a later entry fails"() {
        given:
        def classesClosedCount = new AtomicInteger(0)
        def classesLocator = new CountingClassFileLocator(classesClosedCount)
        def valid = new File(tempDir, "compose-cleanup/valid.jar")
        createEmptyJar(valid)
        def malformed = new File(tempDir, "compose-cleanup/malformed.jar")
        malformed.parentFile.mkdirs()
        malformed.text = "not-a-zip"

        when:
        AotClassFileLocatorFactory.compose(classesLocator, [valid, malformed])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains(malformed.absolutePath)
        classesClosedCount.get() == 1
        AotClassFileLocatorFactory.BOOT_LOADER_LOCATOR.locate("java/lang/Object").isResolved()
    }

    def "compose accumulates suppressed IOExceptions from close failures on the primary throwable"() {
        given:
        def classesLocator = new ThrowingCloseClassFileLocator("classes close failure")
        def valid = new File(tempDir, "compose-suppressed/valid.jar")
        createEmptyJar(valid)
        def malformed = new File(tempDir, "compose-suppressed/malformed.jar")
        malformed.parentFile.mkdirs()
        malformed.text = "not-a-zip"

        when:
        AotClassFileLocatorFactory.compose(classesLocator, [valid, malformed])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains(malformed.absolutePath)
        ex.suppressed.length == 1
        ex.suppressed[0] instanceof IOException
        ex.suppressed[0].message == "classes close failure"
    }

    def "appendFileLocators leaves the target list unchanged for empty input"() {
        given:
        def locators = []

        when:
        AotClassFileLocatorFactory.appendFileLocators([], locators)

        then:
        locators == []
    }

    def "appendFileLocators appends existing entries and skips missing ones"() {
        given:
        def existing = new File(tempDir, "factory/existing.jar")
        createEmptyJar(existing)
        def missing = new File(tempDir, "factory/missing.jar")
        def locators = []

        when:
        AotClassFileLocatorFactory.appendFileLocators([existing, missing], locators)

        then:
        locators.size() == 1
        locators[0].class.simpleName.contains("ForJarFile")
    }

    def "appendFileLocators skips null entries while appending existing entries"() {
        given:
        def existing = new File(tempDir, "factory/existing2.jar")
        createEmptyJar(existing)
        def locators = []

        when:
        AotClassFileLocatorFactory.appendFileLocators([existing, null], locators)

        then:
        noExceptionThrown()
        locators.size() == 1
        locators[0].class.simpleName.contains("ForJarFile")
    }

    def "appendFileLocators ignores existing regular files that are not jars"() {
        given:
        def regularFile = new File(tempDir, "factory/plain.txt")
        regularFile.parentFile.mkdirs()
        regularFile.text = "plain"
        def locators = []

        when:
        AotClassFileLocatorFactory.appendFileLocators([regularFile], locators)

        then:
        locators.isEmpty()
    }

    def "appendFileLocators appends a ForFolder locator for directory entries"() {
        given:
        def classesDir = new File(tempDir, "factory/classes-dir")
        classesDir.mkdirs()
        def locators = []

        when:
        AotClassFileLocatorFactory.appendFileLocators([classesDir], locators)

        then:
        locators.size() == 1
        locators[0].class.simpleName.contains("ForFolder")
    }

    def "appendFileLocators throws IllegalStateException for malformed jar entries"() {
        given:
        def malformed = new File(tempDir, "factory/malformed.jar")
        malformed.parentFile.mkdirs()
        malformed.text = "not-a-jar"
        def locators = []

        when:
        AotClassFileLocatorFactory.appendFileLocators([malformed], locators)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains(malformed.absolutePath)
        ex.cause instanceof IOException
        locators.isEmpty()
    }

    private static List<ClassFileLocator> extractCompoundLocators(ClassFileLocator.Compound compound) {
        return compound.@classFileLocators
    }

    private static File createEmptyJar(File file) {
        file.parentFile.mkdirs()
        new JarOutputStream(new FileOutputStream(file)).close()
        return file
    }

    static class CountingClassFileLocator implements ClassFileLocator {

        private final AtomicInteger closedCount

        CountingClassFileLocator(AtomicInteger closedCount) {
            this.closedCount = closedCount
        }

        @Override
        Resolution locate(String name) {
            return new ClassFileLocator.Resolution.Illegal(name)
        }

        @Override
        void close() {
            closedCount.incrementAndGet()
        }
    }

    static class ThrowingCloseClassFileLocator implements ClassFileLocator {

        private final String failureMessage

        ThrowingCloseClassFileLocator(String failureMessage) {
            this.failureMessage = failureMessage
        }

        @Override
        Resolution locate(String name) {
            return new ClassFileLocator.Resolution.Illegal(name)
        }

        @Override
        void close() throws IOException {
            throw new IOException(failureMessage)
        }
    }

    private static Iterable<File> iterableWithCounter(List<File> files, AtomicInteger nextCalls) {
        return {
            def delegate = files.iterator()
            return new Iterator<File>() {
                @Override
                boolean hasNext() {
                    delegate.hasNext()
                }

                @Override
                File next() {
                    nextCalls.incrementAndGet()
                    delegate.next()
                }
            }
        } as Iterable<File>
    }

    private static Iterable<File> iterableThatFailsOnIterator(String message) {
        return {
            throw new IllegalStateException(message)
        } as Iterable<File>
    }

    private static Iterable<File> iterableThatFailsOnSecondNext(
            File first, RuntimeException error, AtomicInteger nextCalls) {
        return {
            return new Iterator<File>() {
                private int index = 0

                @Override
                boolean hasNext() {
                    index < 3
                }

                @Override
                File next() {
                    nextCalls.incrementAndGet()
                    if (index == 0) {
                        index++
                        return first
                    }
                    throw error
                }
            }
        } as Iterable<File>
    }
}
