package org.libprunus.core.plugin.aot.log.fixture.inspect;

public class InspectGenericService implements InspectGenericPort<String> {

    @Override
    public String process(String input) {
        return input;
    }
}
