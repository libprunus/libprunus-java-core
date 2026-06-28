package com.example.batch;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MethodLoggingProfile;

// libprunus reads this @LogRegistry at build time; the profile routes every
// *Processor under com.example.batch into generated method entry/exit logging.
@LogRegistry
@MethodLoggingProfile(
        includePackages = {"com.example.batch"},
        includeClassSuffixes = {"Processor"})
public final class AppLoggingConvention {

    private AppLoggingConvention() {}
}
