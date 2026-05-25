package org.libprunus.core.plugin.aot.log.fixture.sensitive;

import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

@Sensitive
@DoNotLog
public class FamilyResolutionMultiClassAnnotation {

    public String value;
}
