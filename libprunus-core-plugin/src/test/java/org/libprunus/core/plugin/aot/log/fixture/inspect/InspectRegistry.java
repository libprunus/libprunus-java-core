package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MethodLoggingField;
import org.libprunus.core.log.annotation.MethodLoggingProfile;
import org.libprunus.core.log.annotation.ToStringProfile;
import org.libprunus.core.log.runtime.LogLevel;

@LogRegistry
@MethodLoggingProfile(
        includePackages = {"org.libprunus.core.plugin.aot.log.fixture.inspect"},
        includeClassSuffixes = {"Service"})
@MethodLoggingProfile(
        includePackages = {"org.libprunus.core.plugin.aot.log.fixture.inspect"},
        includeClassSuffixes = {"Adapter"},
        fields = {"traceId"},
        entryLevel = LogLevel.DEBUG,
        exitLevel = LogLevel.DEBUG)
@ToStringProfile(
        includePackages = {"org.libprunus.core.plugin.aot.log.fixture.inspect"},
        includeClassSuffixes = {"Dto"})
public class InspectRegistry {

    @MethodLoggingField("traceId")
    public static String traceId() {
        return "inspect-trace";
    }
}
