package org.libprunus.spring.server.management;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ManagementConfiguration {

    @Bean
    public ManagementController managementController() {
        return new ManagementController();
    }
}
