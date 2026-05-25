package contract;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MaxMessageLength;
import org.libprunus.core.log.annotation.MethodLoggingProfile;
import org.libprunus.core.log.annotation.ToStringProfile;

@LogRegistry
@MaxMessageLength(4096)
@ToStringProfile(
        includePackages = "contract",
        includeClassSuffixes = {"Subject", "Child"})
@MethodLoggingProfile(
        includePackages = "contract",
        includeClassSuffixes = {"Subject", "Child"})
public class LogContextRegistry {}
