package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.DoLog;

public interface InspectRedundantParentPort {

    @DoLog
    String resolve(String key);
}
