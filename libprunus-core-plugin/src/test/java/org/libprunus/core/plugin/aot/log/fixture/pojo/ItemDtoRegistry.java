package org.libprunus.core.plugin.aot.log.fixture.pojo;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.ToStringProfile;

@LogRegistry
@ToStringProfile(
        includePackages = {"org.libprunus.core.plugin.aot.log"},
        includeClassSuffixes = {"Dto"})
public class ItemDtoRegistry {}
