package org.libprunus.core.log.runtime.fixture;

import org.libprunus.core.log.runtime.StringBuilderPool;
import org.libprunus.core.log.runtime.StringBuilderWithContext;

public final class StringBuilderWithContextFallbackThrowableSelectionProbe {

    private StringBuilderWithContextFallbackThrowableSelectionProbe() {
        throw new UnsupportedOperationException();
    }

    public static void main(String[] args) throws Throwable {
        OutOfMemoryError oom = new OutOfMemoryError("fatal");
        StringBuilderWithContext context = StringBuilderPool.acquire();
        try {
            StringBuilderWithContext.recoverToStringFallback("probe", context, oom);
            System.out.println("FALLBACK_THROWABLE_SELECTION_FAILED: expected OOM to be thrown");
            System.exit(1);
        } catch (OutOfMemoryError e) {
            if (e == oom && e.getSuppressed().length == 0) {
                System.out.println("FALLBACK_THROWABLE_SELECTION_OK");
            } else {
                System.out.println("FALLBACK_THROWABLE_SELECTION_FAILED");
                System.exit(1);
            }
        }
    }
}
