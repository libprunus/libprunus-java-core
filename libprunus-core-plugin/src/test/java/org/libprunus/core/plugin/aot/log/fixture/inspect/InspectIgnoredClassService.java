package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;

@AutomatedProcessingIgnore
public class InspectIgnoredClassService {

    public String process(String input) {
        return "ignored-class:" + input;
    }

    public String another(int n) {
        return "ignored-class-another:" + n;
    }
}
