package org.libprunus.spring.config;

import org.libprunus.core.config.ConfigurationRepository;
import org.libprunus.core.config.CoreRuntimeConfig;
import org.libprunus.core.log.runtime.LogRuntime;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

@AutoConfiguration
@EnableConfigurationProperties(CoreRuntimeProperties.class)
public class CoreRuntimeConfigAutoConfiguration implements InitializingBean {

    private final ClassLoader classLoader;

    public CoreRuntimeConfigAutoConfiguration(ResourceLoader resourceLoader) {
        this.classLoader = resourceLoader.getClassLoader();
    }

    @Override
    public void afterPropertiesSet() {
        LogRuntime.invokeCallsiteBinding(classLoader);
    }

    @Bean
    @ConditionalOnMissingBean
    public CoreRuntimeConfig coreRuntimeConfig(CoreRuntimeProperties properties) {
        return properties.toConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConfigurationRepository configurationRepository(CoreRuntimeConfig coreRuntimeConfig) {
        return new ConfigurationRepository(coreRuntimeConfig);
    }
}
