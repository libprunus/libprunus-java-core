package org.libprunus.core.log.runtime

import java.nio.file.Files
import java.nio.file.Path
import spock.lang.Specification
import spock.lang.TempDir

class LogRuntimeCrossClassLoaderIntegrationSpec extends Specification {

    @TempDir
    Path tempRoot

    def setup() {
        LogRuntimeTestSupport.resetBinding()
        FirstTreeProbe.invokeCount = 0
        SecondTreeProbe.invokeCount = 0
    }

    def cleanup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def "invokeCallsiteBinding scans each ClassLoader tree independently — the first call succeeds and the second tree triggers the once-only guard from initializeBinding"() {
        given: "two sibling ClassLoader trees, each carrying its own META-INF/prunus/aot/runtime-binding-callsite resource that points to a distinct probe"
        def firstTreeDir = tempRoot.resolve("tree-one")
        def secondTreeDir = tempRoot.resolve("tree-two")
        writeCallsitePointer(firstTreeDir, FirstTreeProbe.name)
        writeCallsitePointer(secondTreeDir, SecondTreeProbe.name)

        def parent = getClass().classLoader
        def firstTree = new URLClassLoader([firstTreeDir.toUri().toURL()] as URL[], parent)
        def secondTree = new URLClassLoader([secondTreeDir.toUri().toURL()] as URL[], parent)

        and: "each tree resolves its own copy of the callsite resource — getResource returns a URL rooted at that tree's URL list, not the other tree's URL list"
        firstTree.getResource(LogRuntime.CALLSITE_BINDING_RESOURCE).toString().contains("tree-one")
        secondTree.getResource(LogRuntime.CALLSITE_BINDING_RESOURCE).toString().contains("tree-two")

        when: "the first tree drives invokeCallsiteBinding"
        LogRuntime.invokeCallsiteBinding(firstTree)

        then: "the first tree's probe ran to completion — initializeBinding consumed the once-only slot"
        FirstTreeProbe.invokeCount == 1
        SecondTreeProbe.invokeCount == 0
        LogRuntime.getGlobalMaxMessageLength() == 1024

        when: "the second tree also drives invokeCallsiteBinding — its probe attempts initializeBinding a second time"
        LogRuntime.invokeCallsiteBinding(secondTree)

        then: "the second probe ran (its bind() entered) but the wrapping IllegalStateException surfaces the once-only guard from initializeBinding"
        SecondTreeProbe.invokeCount == 1
        def ex = thrown(IllegalStateException)
        ex.message == "Failed to invoke callsite binding for target class: [" + SecondTreeProbe.name + "]"

        and: "the wrapped cause is the once-only IllegalStateException raised inside initializeBinding"
        ex.cause instanceof IllegalStateException
        ex.cause.message == "LogRuntime binding has already been initialized"

        and: "the first tree's binding remained the winner — its 1024 max length still binds globally"
        LogRuntime.getGlobalMaxMessageLength() == 1024

        cleanup:
        firstTree?.close()
        secondTree?.close()
    }

    private static void writeCallsitePointer(Path root, String className) {
        def resourceDir = root.resolve("META-INF/prunus/aot")
        Files.createDirectories(resourceDir)
        Files.writeString(resourceDir.resolve("runtime-binding-callsite"), className)
    }

    static class FirstTreeProbe {
        static volatile int invokeCount = 0

        static void bind() {
            invokeCount++
            LogRuntime.initializeBinding(new AbstractLogConfig() {
                @Override int getMaxMessageLength() { return 1024 }
                @Override boolean isWhitelisted(Class<?> type) { return type == String.class }
            })
        }
    }

    static class SecondTreeProbe {
        static volatile int invokeCount = 0

        static void bind() {
            invokeCount++
            LogRuntime.initializeBinding(new AbstractLogConfig() {
                @Override int getMaxMessageLength() { return 2048 }
                @Override boolean isWhitelisted(Class<?> type) { return type == Integer.class }
            })
        }
    }
}
