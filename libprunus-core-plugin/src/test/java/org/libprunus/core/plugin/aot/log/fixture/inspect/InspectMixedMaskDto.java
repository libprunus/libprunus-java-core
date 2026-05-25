package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.Sensitive;

@Sensitive
public class InspectMixedMaskDto {

    public String masked;

    @DoLog
    public String unmasked;
}
