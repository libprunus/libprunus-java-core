package org.libprunus.core.plugin.aot.log.fixture.methodplan;

import org.libprunus.core.log.annotation.DoNotLog;

interface LogOutputIgnoreDiamondLeft {

    @DoNotLog
    String resolve(@DoNotLog String input);
}
