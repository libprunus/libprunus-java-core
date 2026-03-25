package org.libprunus.spring.config;

import org.libprunus.core.config.ConfigurationRepository;
import org.libprunus.core.config.CoreRuntimeConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CoreRuntimeProperties.class)
public class CoreRuntimeConfigAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CoreRuntimeConfig coreRuntimeConfig(CoreRuntimeProperties properties) {
        return new CoreRuntimeConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConfigurationRepository configurationRepository(CoreRuntimeConfig coreRuntimeConfig) {
        return new ConfigurationRepository(coreRuntimeConfig);
    }
}
