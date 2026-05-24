package org.libprunus.spring.config;

import org.libprunus.core.config.CoreRuntimeConfig;
import org.libprunus.core.log.runtime.LogRuntimeConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "libprunus")
public class CoreRuntimeProperties {

    private LogRuntimeConfig log = new LogRuntimeConfig(true);

    public LogRuntimeConfig getLog() {
        return log;
    }

    public void setLog(LogRuntimeConfig log) {
        this.log = log;
    }

    public CoreRuntimeConfig toConfig() {
        return new CoreRuntimeConfig(log);
    }
}
