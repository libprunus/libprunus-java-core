package org.libprunus.core.plugin.aot.util;

import java.io.IOException;

public final class ResourceLimitExceededException extends IOException {

    public ResourceLimitExceededException(String message) {
        super(message);
    }
}
