package org.libprunus.core.plugin.aot.log.fixture.registry;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MethodLoggingField;
import org.libprunus.core.log.annotation.MethodLoggingProfile;

@LogRegistry
@MethodLoggingProfile(
        includePackages = {"sample.svc"},
        includeClassSuffixes = {"Service"},
        fields = {"x"})
class PackagePrivateFieldRegistry {

    @MethodLoggingField("x")
    public static String x() {
        return "x";
    }
}
