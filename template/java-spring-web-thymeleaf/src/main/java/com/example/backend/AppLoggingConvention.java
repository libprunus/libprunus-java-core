package com.example.backend;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MethodLoggingProfile;

// libprunus reads this @LogRegistry at build time; the profile routes every
// *Service under com.example.backend into generated method entry/exit logging.
@LogRegistry
@MethodLoggingProfile(
        includePackages = {"com.example.backend"},
        includeClassSuffixes = {"Service"})
public final class AppLoggingConvention {

    private AppLoggingConvention() {}
}
