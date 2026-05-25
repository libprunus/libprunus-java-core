package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public class InspectChainLeafDto extends InspectChainMidDto {

    public String leafPubNone;

    @Sensitive
    public String leafPubAll;

    @DoNotLog
    public String leafPubIgnored;

    private String leafPrivNone;

    @Sensitive
    private String leafPrivAll;
}
