package org.libprunus.spring.config.json;

import java.util.List;
import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.DefaultBaseTypeLimitingValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Polymorphic-type validator that composes two defenses: it keeps Jackson's default unsafe-base-type
 * limiting (denies {@code Object}, {@code Serializable}, ... as base types) and additionally only
 * resolves subtypes whose class name lies within an allow-listed package. Installing a bare subtype
 * allow-list would replace — and thereby drop — Jackson's base-type defense, so both are retained.
 */
final class BaseTypeLimitingPackageValidator extends PolymorphicTypeValidator.Base {

    private final PolymorphicTypeValidator baseTypeLimit = new DefaultBaseTypeLimitingValidator();
    private final PolymorphicTypeValidator packageAllowList;

    BaseTypeLimitingPackageValidator(List<String> allowedPackages) {
        var allowList = BasicPolymorphicTypeValidator.builder();
        for (String allowedPackage : allowedPackages) {
            // Match on the package boundary: append the separator so "com.app" cannot also
            // match a sibling package "com.application".
            var prefix = allowedPackage.endsWith(".") ? allowedPackage : allowedPackage + ".";
            allowList.allowIfSubType(prefix);
        }
        this.packageAllowList = allowList.build();
    }

    @Override
    public Validity validateBaseType(DatabindContext context, JavaType baseType) {
        return baseTypeLimit.validateBaseType(context, baseType);
    }

    @Override
    public Validity validateSubClassName(DatabindContext context, JavaType baseType, String subClassName) {
        return packageAllowList.validateSubClassName(context, baseType, subClassName);
    }

    @Override
    public Validity validateSubType(DatabindContext context, JavaType baseType, JavaType subType) {
        return packageAllowList.validateSubType(context, baseType, subType);
    }
}
