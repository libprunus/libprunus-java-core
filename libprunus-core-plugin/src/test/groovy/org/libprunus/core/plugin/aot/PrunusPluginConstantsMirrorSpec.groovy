package org.libprunus.core.plugin.aot

import org.libprunus.core.log.annotation.DoLog
import org.libprunus.core.log.annotation.DoNotLog
import org.libprunus.core.log.annotation.Sensitive
import org.libprunus.core.log.runtime.AbstractLogConfig
import spock.lang.Specification

class PrunusPluginConstantsMirrorSpec extends Specification {

    def "ABSTRACT_LOG_CONFIG_FQCN names a class loadable via the runtime classpath"() {
        when:
        def loaded = Class.forName(PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN)

        then:
        loaded.is(AbstractLogConfig.class)
        PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN == AbstractLogConfig.class.name
    }

    def "SENSITIVE_ANNOTATION_BINARY_NAME names the Sensitive annotation"() {
        when:
        def loaded = Class.forName(PrunusPluginConstants.SENSITIVE_ANNOTATION_BINARY_NAME)

        then:
        loaded.is(Sensitive.class)
        PrunusPluginConstants.SENSITIVE_ANNOTATION_BINARY_NAME == Sensitive.class.name
    }

    def "DO_NOT_LOG_ANNOTATION_BINARY_NAME names the DoNotLog annotation"() {
        when:
        def loaded = Class.forName(PrunusPluginConstants.DO_NOT_LOG_ANNOTATION_BINARY_NAME)

        then:
        loaded.is(DoNotLog.class)
        PrunusPluginConstants.DO_NOT_LOG_ANNOTATION_BINARY_NAME == DoNotLog.class.name
    }

    def "DO_LOG_ANNOTATION_BINARY_NAME names the DoLog annotation"() {
        when:
        def loaded = Class.forName(PrunusPluginConstants.DO_LOG_ANNOTATION_BINARY_NAME)

        then:
        loaded.is(DoLog.class)
        PrunusPluginConstants.DO_LOG_ANNOTATION_BINARY_NAME == DoLog.class.name
    }
}
