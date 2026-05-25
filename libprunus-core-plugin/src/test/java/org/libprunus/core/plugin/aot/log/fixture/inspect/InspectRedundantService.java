package org.libprunus.core.plugin.aot.log.fixture.inspect;

public class InspectRedundantService implements InspectRedundantChildPort, InspectRedundantParentPort {

    @Override
    public String resolve(String key) {
        return key;
    }
}
