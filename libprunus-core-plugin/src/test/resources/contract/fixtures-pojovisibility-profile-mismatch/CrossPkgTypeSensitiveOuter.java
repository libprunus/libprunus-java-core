package org.libprunus.core.plugin.aot.log.fixture.pojovisibility.crosspkg.outer;

import org.libprunus.core.log.annotation.Sensitive;

@Sensitive
public class CrossPkgTypeSensitiveOuter {

    public String publicOuterField = "public-outer-val";
}
