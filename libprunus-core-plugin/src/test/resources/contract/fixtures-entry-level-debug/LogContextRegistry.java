package contract;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MaxMessageLength;
import org.libprunus.core.log.annotation.MethodLoggingProfile;
import org.libprunus.core.log.runtime.LogLevel;

@LogRegistry
@MaxMessageLength(4096)
@MethodLoggingProfile(
        includePackages = "contract",
        includeClassSuffixes = {"Service"},
        entryLevel = LogLevel.DEBUG)
public class LogContextRegistry {}
