package org.libprunus.core.plugin.aot.log.fixture.inspect;

public class InspectPortAdapter implements InspectPort {

    @Override
    public String fetch(String key, String context) {
        return key + ":" + context;
    }
}
