package org.libprunus.core.plugin.aot.log.fixture.sensitive;

import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public class FamilyResolutionMultiParamAnnotation {

    public String collide(String first, String second, @Sensitive @DoNotLog String tainted) {
        return first + second + tainted;
    }
}
