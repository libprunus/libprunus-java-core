package org.libprunus.core.plugin.aot.log.fixture.sensitive;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

@Sensitive
@DoNotLog
@DoLog
public class FamilyResolutionTripleClassAnnotation {

    public String value;
}
