package org.libprunus.core.plugin.aot.log.fixture.pojovisibility.crosspkg.outer;

public interface CrossPkgUnmatchedIface {

    default String defaultMethodValue() {
        return "iface-default-val";
    }
}
