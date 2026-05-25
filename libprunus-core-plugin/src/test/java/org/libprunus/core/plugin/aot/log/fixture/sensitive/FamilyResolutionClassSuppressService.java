package org.libprunus.core.plugin.aot.log.fixture.sensitive;

import org.libprunus.core.log.annotation.DoNotLog;

@DoNotLog
public class FamilyResolutionClassSuppressService {

    public String fallbackSuppressed;

    public String process(String input) {
        return input;
    }
}
