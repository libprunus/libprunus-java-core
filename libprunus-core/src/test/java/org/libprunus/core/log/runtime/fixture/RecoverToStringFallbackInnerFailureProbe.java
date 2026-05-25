package org.libprunus.core.log.runtime.fixture;

import java.lang.reflect.Field;
import org.libprunus.core.log.runtime.StringBuilderPool;
import org.libprunus.core.log.runtime.StringBuilderWithContext;

/**
 * Drives the inner-failure branches of
 * {@link StringBuilderWithContext#recoverToStringFallback(String, StringBuilderWithContext, Throwable)}.
 *
 * <p>The probe corrupts {@link StringBuilder}'s package-private {@code count} field via reflection
 * so the backing builder's {@code toString()} call inside the recovery's nested try throws either
 * a non-SOE {@link Error} (U-5: triggered by {@code count = Integer.MAX_VALUE}, which forces the
 * String constructor to attempt allocating an oversized backing array) or a non-Error
 * {@link Throwable} (U-6: triggered by {@code count = -1}, which produces an
 * {@link IllegalArgumentException} inside {@code Arrays.copyOfRange}).
 *
 * <p>Run with {@code --add-opens java.base/java.lang=ALL-UNNAMED} so reflection over
 * {@link StringBuilder}'s package-private fields succeeds.
 *
 * <h2>Verifiable boundaries</h2>
 * The reflection-based fault injection has two hard limits that this probe is explicit about:
 * <ul>
 *   <li>The OOM raised by oversized-array allocation is a JVM-internal pre-allocated instance
 *       whose {@code suppressedExceptions} field is disabled. The production
 *       {@code addSuppressed(throwable)} call therefore becomes a no-op even though the source
 *       branch is exercised. The probe records {@code ORIGINAL_SUPPRESSED} for diagnostic
 *       transparency without making it a pass/fail signal.
 *   <li>The U-6 corruption ({@code count = -1}) also breaks {@code setLength(0)} that
 *       {@link StringBuilderPool#release} invokes during reset, because
 *       {@code AbstractStringBuilder.setLength} enters {@code Arrays.fill(value, count, ...)} when
 *       {@code count < newLength}. The probe therefore observes an
 *       {@link ArrayIndexOutOfBoundsException} bubbling out of release. The non-Error catch
 *       branch is still verified to have run via the {@code libprunus logging failure} marker on
 *       stderr (captured here through {@code redirectErrorStream}).
 * </ul>
 *
 * <p>Modes (selected by {@code args[0]}):
 * <ul>
 *   <li>{@code inner-non-soe-error} — verifies U-5: OOM is rethrown from the recovery method.
 *   <li>{@code inner-non-error-throwable} — verifies U-6: the non-Error catch branch is entered
 *       (logging side effect observed) before release downstream effects.
 * </ul>
 */
public final class RecoverToStringFallbackInnerFailureProbe {

    private RecoverToStringFallbackInnerFailureProbe() {
        throw new UnsupportedOperationException();
    }

    public static void main(String[] args) throws Throwable {
        if (args.length == 0) {
            System.out.println("PROBE_FAILED_NO_MODE");
            System.exit(1);
            return;
        }
        String mode = args[0];
        switch (mode) {
            case "inner-non-soe-error" -> runInnerNonSoeError();
            case "inner-non-error-throwable" -> runInnerNonErrorThrowable();
            default -> {
                System.out.println("PROBE_FAILED_UNKNOWN_MODE_" + mode);
                System.exit(1);
            }
        }
    }

    private static void runInnerNonSoeError() throws Exception {
        StringBuilderWithContext context = StringBuilderPool.acquire();
        StringBuilder backing = readBuilder(context);
        backing.append("seed");
        // count = Integer.MAX_VALUE forces String constructor to allocate ~2GB backing array,
        // which uniformly raises OutOfMemoryError on every JVM build.
        setCount(backing, Integer.MAX_VALUE);

        RuntimeException original = new RuntimeException("upstream-render-failed");
        try {
            StringBuilderWithContext.recoverToStringFallback("probe", context, original);
            System.out.println("INNER_NON_SOE_ERROR_FAILED_NO_THROW");
            System.exit(1);
        } catch (OutOfMemoryError caught) {
            // Diagnostic-only: VM pre-allocated OOM has enableSuppression=false, so
            // addSuppressed is silently dropped by the JVM. Print the observed values without
            // gating on them.
            boolean originalSuppressed = false;
            for (Throwable s : caught.getSuppressed()) {
                if (s == original) {
                    originalSuppressed = true;
                    break;
                }
            }
            System.out.println("INNER_NON_SOE_ERROR_OK");
            System.out.println("ERROR_TYPE=" + caught.getClass().getName());
            System.out.println("ORIGINAL_SUPPRESSED=" + originalSuppressed);
        } catch (Throwable other) {
            System.out.println(
                    "INNER_NON_SOE_ERROR_FAILED_TYPE_" + other.getClass().getName());
            System.exit(1);
        }
    }

    private static void runInnerNonErrorThrowable() throws Exception {
        StringBuilderWithContext context = StringBuilderPool.acquire();
        StringBuilder backing = readBuilder(context);
        backing.append("seed");
        // count = -1 makes new String(byte[], 0, count, coder) raise IllegalArgumentException
        // ("0 > -1") inside Arrays.copyOfRange — a non-Error Throwable, the exact U-6 trigger.
        setCount(backing, -1);

        RuntimeException original = new RuntimeException("upstream-render-failed");
        Throwable releaseLeak = null;
        String fallback = null;
        try {
            fallback = StringBuilderWithContext.recoverToStringFallback("probe", context, original);
        } catch (ArrayIndexOutOfBoundsException releaseAioobe) {
            // Expected secondary effect: StringBuilderPool.release calls reset(0) →
            // setLength(0), and AbstractStringBuilder.setLength fills the slot range using the
            // corrupted negative count, raising AIOOBE. Its presence proves line 491 release ran
            // (and therefore the non-Error catch branch on line 489 ran first).
            releaseLeak = releaseAioobe;
        } catch (Throwable unexpected) {
            System.out.println("INNER_NON_ERROR_THROWABLE_FAILED_TYPE_"
                    + unexpected.getClass().getName());
            unexpected.printStackTrace(System.out);
            System.exit(1);
            return;
        }

        // Verify the non-Error catch branch executed: LoggingFailureReporter wrote a marker for
        // the inner IllegalArgumentException to stderr, captured through redirectErrorStream.
        // (We can't read stderr from inside the probe; the caller asserts on the merged stream.)
        System.out.println("INNER_NON_ERROR_THROWABLE_OK");
        System.out.println("INNER_CATCH_BRANCH_RAN=" + (releaseLeak != null || fallback != null));
        System.out.println("RELEASE_AIOOBE_OBSERVED=" + (releaseLeak != null));
        if (fallback != null) {
            System.out.println("FALLBACK_IS_EMPTY=" + "".equals(fallback));
        }
    }

    private static StringBuilder readBuilder(StringBuilderWithContext context) throws Exception {
        Field builderField = StringBuilderWithContext.class.getDeclaredField("builder");
        builderField.setAccessible(true);
        return (StringBuilder) builderField.get(context);
    }

    private static void setCount(StringBuilder builder, int newCount) throws Exception {
        Class<?> abstractStringBuilder = StringBuilder.class.getSuperclass();
        Field countField = abstractStringBuilder.getDeclaredField("count");
        countField.setAccessible(true);
        countField.setInt(builder, newCount);
    }
}
