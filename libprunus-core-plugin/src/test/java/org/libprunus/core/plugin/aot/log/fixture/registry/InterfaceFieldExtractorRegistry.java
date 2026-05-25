package org.libprunus.core.plugin.aot.log.fixture.registry;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MethodLoggingField;
import org.libprunus.core.log.annotation.MethodLoggingProfile;

@LogRegistry
@MethodLoggingProfile(
        includePackages = {"sample.svc"},
        includeClassSuffixes = {"Service"},
        fields = {"traceId"})
public interface InterfaceFieldExtractorRegistry {

    @MethodLoggingField("traceId")
    static String traceId() {
        return "t1";
    }
}
