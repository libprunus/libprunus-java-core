package com.example.registry;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MaxMessageLength;
import org.libprunus.core.log.annotation.MethodLoggingProfile;

@LogRegistry
@MaxMessageLength(4096)
@MethodLoggingProfile(
        includePackages = {"com.example"},
        includeClassSuffixes = {"Target"})
@MethodLoggingProfile(
        includePackages = {"com.example"},
        includeClassSuffixes = {"ProfileTarget"})
public class LogContextRegistry {}
