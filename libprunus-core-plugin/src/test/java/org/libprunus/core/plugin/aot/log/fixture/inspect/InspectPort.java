package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public interface InspectPort {

    @Sensitive
    String fetch(@DoNotLog String key, String context);
}
