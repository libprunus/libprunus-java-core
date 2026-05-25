package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.Sensitive;

@Sensitive
public class InspectClassAnnotatedService {

    public String greet(String name, String greeting) {
        return greeting + ", " + name;
    }
}
