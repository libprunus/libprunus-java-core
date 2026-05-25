package org.libprunus.core.plugin.aot.log.fixture.sensitive;

import org.libprunus.core.log.annotation.DoLog;

@DoLog
public class FamilyResolutionClassPassThroughService {

    public String fallbackPassThrough;

    public String process(String input) {
        return input;
    }
}
