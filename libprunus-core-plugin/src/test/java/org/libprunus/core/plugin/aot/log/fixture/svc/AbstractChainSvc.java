package org.libprunus.core.plugin.aot.log.fixture.svc;

import org.libprunus.core.log.annotation.Sensitive;

@Sensitive
public abstract class AbstractChainSvc {

    public abstract String fetch(String query);
}
