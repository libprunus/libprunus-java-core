package org.libprunus.core.plugin.aot.log.fixture.svc;

public class ConcreteChainSvc extends MidChainSvc {

    @Override
    public String fetch(String query) {
        return "result:" + query;
    }
}
