package org.libprunus.spring.config.json;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hardens class-name polymorphic deserialization for the Jackson 3 {@link JsonMapper}. Jackson 3's
 * default validator already denies unsafe base types (Object, Serializable, ...); this config keeps
 * that base-type limiting AND additionally restricts resolvable subtypes to the packages listed in
 * {@code prunus.json.allowed-packages} (empty default = no class-name subtype is resolvable, so
 * class-name polymorphic typing is opt-in per package). Explicitly-registered
 * {@code @JsonTypeInfo(use = NAME)} subtypes are not class-name resolution and are unaffected; a
 * downstream that calls {@code activateDefaultTyping} with its own validator bypasses this gate.
 *
 * <p>To override, define a {@link JsonMapperBuilderCustomizer} bean named
 * {@code prunusPolymorphicTypeValidatorCustomizer} — doing so replaces, and can disable, this
 * security default.
 */
@AutoConfiguration
@ConditionalOnClass({JsonMapper.class, JsonMapperBuilderCustomizer.class})
@EnableConfigurationProperties(SafeJacksonProperties.class)
public class SafeJacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "prunusPolymorphicTypeValidatorCustomizer")
    public JsonMapperBuilderCustomizer prunusPolymorphicTypeValidatorCustomizer(SafeJacksonProperties properties) {
        var validator = new BaseTypeLimitingPackageValidator(properties.allowedPackages());
        return builder -> builder.polymorphicTypeValidator(validator);
    }
}
