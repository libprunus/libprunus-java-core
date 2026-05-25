package org.libprunus.core.plugin.aot.log.fixture.sensitive;

import org.libprunus.core.log.annotation.Sensitive;

@Sensitive
public class FamilyResolutionClassMaskService {

    public String fallbackMasked;

    public String process(String input) {
        return input;
    }
}
