package org.libprunus.core.log.runtime.fixture;

import java.util.ArrayList;
import java.util.List;
import org.libprunus.core.log.runtime.StringBuilderPool;
import org.libprunus.core.log.runtime.StringBuilderWithContext;

public final class StringBuilderWithContextToStringOomSelectionProbe {

    private StringBuilderWithContextToStringOomSelectionProbe() {
        throw new UnsupportedOperationException();
    }

    public static void main(String[] args) throws Throwable {
        StringBuilderWithContext.reportLoggingFailure("probe.init", null);

        RuntimeException original = new RuntimeException("upstream-render-failed");
        StringBuilderWithContext context = StringBuilderPool.acquire();
        context.setMaxMessageLength(Integer.MAX_VALUE);

        StringBuilder chunk = new StringBuilder(256 * 1024);
        for (int i = 0; i < 256 * 1024; i++) {
            chunk.append('x');
        }
        for (int i = 0; i < 16; i++) {
            context.append((CharSequence) chunk);
        }
        chunk.setLength(0);
        chunk = null;
        System.gc();

        List<byte[]> hogs = new ArrayList<>();
        try {
            while (true) {
                hogs.add(new byte[128 * 1024]);
            }
        } catch (OutOfMemoryError _) {
            // expected: heap is now saturated
        }
        for (int i = 0; i < 4 && !hogs.isEmpty(); i++) {
            hogs.remove(hogs.size() - 1);
        }

        try {
            StringBuilderWithContext.recoverToStringFallback("probe", context, original);
            hogs.clear();
            hogs = null;
            System.out.println("TOSTRING_OOM_SELECTION_FAILED_NO_OOM");
            System.exit(1);
        } catch (OutOfMemoryError _) {
            hogs.clear();
            hogs = null;
            // Primary direction: the toString OOM (an Error) was selected over the original RuntimeException.
            // The VM's pre-allocated OOM disables suppression, so the production addSuppressed is a no-op here
            // and isn't asserted — asserting it would need a non-VM Error, impossible without final-class mocking.
            System.out.println("TOSTRING_OOM_SELECTION_OK");
        } catch (Throwable other) {
            hogs.clear();
            hogs = null;
            System.out.println(
                    "TOSTRING_OOM_SELECTION_FAILED_TYPE_" + other.getClass().getName());
            System.exit(1);
        }
    }
}
