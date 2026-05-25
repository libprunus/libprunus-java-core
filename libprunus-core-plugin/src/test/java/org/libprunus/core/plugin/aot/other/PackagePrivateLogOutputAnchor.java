package org.libprunus.core.plugin.aot.other;

import org.libprunus.core.log.annotation.Sensitive;

public abstract class PackagePrivateLogOutputAnchor {

    @Sensitive
    String fetch(String input) {
        return input;
    }
}
