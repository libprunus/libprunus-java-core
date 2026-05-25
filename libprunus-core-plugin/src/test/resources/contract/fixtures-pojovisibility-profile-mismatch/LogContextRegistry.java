package org.libprunus.core.plugin.aot.log.fixture.pojovisibility.registry;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MaxMessageLength;
import org.libprunus.core.log.annotation.ToStringProfile;

@LogRegistry
@MaxMessageLength(4096)
@ToStringProfile(
        includePackages = {
            "org.libprunus.core.plugin.aot.log.fixture.pojovisibility.crosspkg.matched",
            "org.libprunus.core.plugin.aot.log.fixture.pojovisibility.samepkg"
        },
        includeClassSuffixes = {"Subject"})
public class LogContextRegistry {}
