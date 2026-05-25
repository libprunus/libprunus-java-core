package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.Sensitive;

public interface InspectRedundantChildPort extends InspectRedundantParentPort {

    @Override
    @Sensitive
    String resolve(String key);
}
