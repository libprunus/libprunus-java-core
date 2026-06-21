package org.libprunus.spring.error;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Registers {@link ApiErrorHandler} for servlet web apps; a downstream {@code ApiErrorHandler} bean replaces it. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(ResponseEntityExceptionHandler.class)
public class ApiErrorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApiErrorHandler apiErrorHandler() {
        return new ApiErrorHandler();
    }
}
