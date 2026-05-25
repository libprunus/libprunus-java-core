package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public class InspectChainRootDto {

    public String rootPubNone;

    @Sensitive
    public String rootPubAll;

    @DoNotLog
    public String rootPubIgnored;

    protected String rootProtNone;

    @Sensitive
    protected String rootProtAll;

    private String rootPrivNone;

    @Sensitive
    private String rootPrivAll;
}
