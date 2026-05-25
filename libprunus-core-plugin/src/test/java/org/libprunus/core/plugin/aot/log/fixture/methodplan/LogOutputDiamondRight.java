package org.libprunus.core.plugin.aot.log.fixture.methodplan;

import org.libprunus.core.log.annotation.DoLog;

interface LogOutputDiamondRight {

    @DoLog
    String resolve(String key);
}
