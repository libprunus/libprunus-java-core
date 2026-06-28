package org.libprunus.spring.config

import java.nio.file.Files
import java.nio.file.Path
import org.libprunus.core.config.ConfigurationRepository
import org.libprunus.core.config.CoreRuntimeConfig
import org.libprunus.core.log.runtime.AbstractLogConfig
import org.libprunus.core.log.runtime.LogRuntime
import org.libprunus.core.log.runtime.LogRuntimeConfig
import org.springframework.core.io.DefaultResourceLoader
import spock.lang.Specification
import spock.lang.TempDir

class CoreRuntimeConfigAutoConfigurationSpec extends Specification {

    @TempDir
    Path tempDir

    private final CoreRuntimeConfigAutoConfiguration autoConfiguration =
            new CoreRuntimeConfigAutoConfiguration(new DefaultResourceLoader())

    def "materializes runtime config from properties on every call"() {
        given: "a properties object"
        def properties = new CoreRuntimeProperties()
        properties.log = new LogRuntimeConfig(false)

        when: "runtime config is requested twice"
        def first = autoConfiguration.coreRuntimeConfig(properties)
        def second = autoConfiguration.coreRuntimeConfig(properties)

        then: "each call returns a distinct snapshot carrying the configured value"
        !first.is(second)
        first == second
        !first.log().enabled()
    }

    def "wraps the supplied runtime config in a repository without altering it"() {
        given: "a runtime config"
        def runtimeConfig = new CoreRuntimeConfig(new LogRuntimeConfig(true))

        when: "a repository is created"
        ConfigurationRepository repository = autoConfiguration.configurationRepository(runtimeConfig)

        then: "the repository exposes the same snapshot instance"
        repository.getGlobalSnapshot().is(runtimeConfig)
    }

    def "afterPropertiesSet is a no-op when no AOT callsite resource is present"() {
        when: "the lifecycle callback runs against a classloader without callsite resource"
        autoConfiguration.afterPropertiesSet()

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "afterPropertiesSet invokes the callsite binding once then skips it once the slot is already consumed"() {
        given: "a reset binding slot and a classloader exposing a callsite pointer to a publishing probe"
        resetBindingState()
        def resourceDir = tempDir.resolve("META-INF/prunus/aot")
        Files.createDirectories(resourceDir)
        Files.writeString(resourceDir.resolve("runtime-binding-callsite"), BindingProbe.name)
        BindingProbe.bindCount = 0
        def loader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], getClass().classLoader)
        def guardedAutoConfiguration = new CoreRuntimeConfigAutoConfiguration(new DefaultResourceLoader(loader))

        when: "the first context boot runs the lifecycle callback"
        guardedAutoConfiguration.afterPropertiesSet()

        then: "the callsite bound exactly once and the slot is now consumed"
        BindingProbe.bindCount == 1
        LogRuntime.isBindingInitialized()

        when: "a second context boot runs the same callback in the same JVM"
        guardedAutoConfiguration.afterPropertiesSet()

        then: "the guard skips the redundant invocation: no exception and no second bind"
        noExceptionThrown()
        BindingProbe.bindCount == 1

        cleanup:
        loader?.close()
        resetBindingState()
    }

    // Spring's test classpath has no access to core's LogRuntimeTestSupport; mirror its reset of the
    // JVM-global binding so this spec neither depends on nor leaks that once-only state.
    private static void resetBindingState() {
        LogRuntime.boundConfig = AbstractLogConfig.DEFAULT
        LogRuntime.boundMaxMessageLength = AbstractLogConfig.DEFAULT.maxMessageLength
        LogRuntime.bindingInitialized = false
    }

    static class BindingProbe {
        static volatile int bindCount = 0
        static void bind() {
            bindCount++
            LogRuntime.initializeBinding(new AbstractLogConfig() {
                @Override int getMaxMessageLength() { return 512 }
                @Override boolean isWhitelisted(Class<?> type) { return false }
            })
        }
    }
}
