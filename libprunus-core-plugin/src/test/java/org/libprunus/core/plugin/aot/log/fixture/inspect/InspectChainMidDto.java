package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public class InspectChainMidDto extends InspectChainRootDto {

    public String midPubNone;

    @Sensitive
    public String midPubAll;

    @DoNotLog
    public String midPubIgnored;
}
