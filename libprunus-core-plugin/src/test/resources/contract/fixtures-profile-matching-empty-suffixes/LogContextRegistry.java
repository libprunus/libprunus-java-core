package com.example.registry;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MaxMessageLength;
import org.libprunus.core.log.annotation.ToStringProfile;

@LogRegistry
@MaxMessageLength(4096)
@ToStringProfile(
        includePackages = {"com.example"},
        includeClassSuffixes = {"", " "})
public class LogContextRegistry {}
