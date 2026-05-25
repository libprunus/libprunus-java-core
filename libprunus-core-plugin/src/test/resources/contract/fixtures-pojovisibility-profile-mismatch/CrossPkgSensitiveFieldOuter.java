package org.libprunus.core.plugin.aot.log.fixture.pojovisibility.crosspkg.outer;

import org.libprunus.core.log.annotation.Sensitive;

public class CrossPkgSensitiveFieldOuter {

    @Sensitive
    public String sensitiveOuterField = "sensitive-outer-val";
}
