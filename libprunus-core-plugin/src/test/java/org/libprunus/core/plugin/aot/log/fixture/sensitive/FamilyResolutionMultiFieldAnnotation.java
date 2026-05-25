package org.libprunus.core.plugin.aot.log.fixture.sensitive;

import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public class FamilyResolutionMultiFieldAnnotation {

    @Sensitive
    @DoNotLog
    public String tainted;
}
