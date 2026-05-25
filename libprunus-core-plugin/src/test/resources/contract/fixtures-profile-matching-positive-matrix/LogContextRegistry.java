package com.example.registry;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MaxMessageLength;
import org.libprunus.core.log.annotation.MethodLoggingProfile;
import org.libprunus.core.log.annotation.ToStringProfile;

@LogRegistry
@MaxMessageLength(4096)
@ToStringProfile(
        includePackages = {"com.beta"},
        includeClassSuffixes = {"Dto"})
@ToStringProfile(
        includePackages = {"com.example"},
        excludePackages = {"com.example.internal"},
        includeClassSuffixes = {"Dto", "Response"})
@MethodLoggingProfile(
        includePackages = {"com.example"},
        includeClassSuffixes = {"Service"})
public class LogContextRegistry {}
