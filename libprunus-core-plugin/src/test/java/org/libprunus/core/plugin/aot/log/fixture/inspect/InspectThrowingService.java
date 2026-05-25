package org.libprunus.core.plugin.aot.log.fixture.inspect;

public class InspectThrowingService {

    public String process(String input) {
        throw new RuntimeException("business-error");
    }
}
