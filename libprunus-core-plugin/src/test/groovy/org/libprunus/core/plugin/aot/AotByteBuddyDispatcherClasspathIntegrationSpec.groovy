package org.libprunus.core.plugin.aot

import net.bytebuddy.dynamic.ClassFileLocator
import spock.lang.Specification
import spock.lang.TempDir

class AotByteBuddyDispatcherClasspathIntegrationSpec extends Specification {

    @TempDir
    File tempDir

    def "constructor deduplicates repeated classpath entries and preserves order"() {
        given:
        def classesDir = new File(tempDir, "classes")
        def first = new File(tempDir, "libs/first")
        def second = new File(tempDir, "libs/second")
        classesDir.mkdirs()
        first.mkdirs()
        second.mkdirs()

        when:
        def dispatcher = new AotByteBuddyDispatcher("", classesDir, [first, first, classesDir, second, first] as File[])

        then:
        dispatcher.@plugins == []
        extractCompoundLocators(dispatcher.@pluginClassFileLocator) == [classesDir, first, second]

        cleanup:
        dispatcher.close()
    }

    private static List<File> extractCompoundLocators(ClassFileLocator locator) {
        def field = locator.class.declaredFields.find { List.isAssignableFrom(it.type) }
        assert field != null
        field.accessible = true
        def locators = field.get(locator) as List<ClassFileLocator>
        locators.collect { extractLocatorRoot(it) }
    }

    private static File extractLocatorRoot(ClassFileLocator locator) {
        def field = locator.class.declaredFields.find { File.isAssignableFrom(it.type) }
        assert field != null
        field.accessible = true
        field.get(locator) as File
    }
}
