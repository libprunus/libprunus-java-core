package org.libprunus.core.plugin.aot.log.fixture.pojovisibility.crosspkg.outer;

import org.libprunus.core.log.annotation.DoNotLog;

public class CrossPkgDoNotLogFieldOuter {

    @DoNotLog
    public String doNotLogOuterField = "donotlog-outer-val";
}
