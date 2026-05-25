package org.libprunus.core.plugin.aot.log.fixture.sensitive;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.Sensitive;

public class FamilyResolutionMultiMethodAnnotation {

    @Sensitive
    @DoLog
    public String conflict(String input) {
        return input;
    }
}
