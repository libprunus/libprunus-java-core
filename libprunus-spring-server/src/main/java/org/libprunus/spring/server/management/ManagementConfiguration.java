package org.libprunus.spring.server.management;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ManagementConfiguration {

    @Bean
    public ManagementController managementController() {
        return new ManagementController();
    }
}
