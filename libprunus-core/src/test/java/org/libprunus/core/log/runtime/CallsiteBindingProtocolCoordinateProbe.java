package org.libprunus.core.log.runtime;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Subprocess probe that writes the AOT callsite resource at the exact coordinate exposed by
 * {@link CallsiteBindingProtocol#RESOURCE_PATH} (no hard-coded literal) and then invokes
 * {@link LogRuntime#invokeCallsiteBinding(ClassLoader)} to prove the same coordinate is what the
 * runtime reads from. Any drift between producer (this probe, via the protocol constant) and
 * consumer (LogRuntime, via its own internal binding) would break the binding path end-to-end and
 * cause {@code BOUND_OK=false} on stdout.
 *
 * <p>Run in a fresh JVM (LogRuntime carries process-global once-only binding state).
 *
 * <p>Output contract:
 * <ul>
 *   <li>{@code RESOURCE_PATH=...} — the protocol coordinate the producer wrote at.
 *   <li>{@code BOUND_OK=true|false} — whether the callsite was resolved and bind() succeeded.
 *   <li>{@code MAX_LENGTH=4096} — proof bind() actually ran (the probe's bind() sets this).
 * </ul>
 */
public final class CallsiteBindingProtocolCoordinateProbe {

    private CallsiteBindingProtocolCoordinateProbe() {
        throw new UnsupportedOperationException();
    }

    public static void bind() {
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override
            public int getMaxMessageLength() {
                return 4096;
            }

            @Override
            public boolean isWhitelisted(Class<?> type) {
                return false;
            }
        });
    }

    public static void main(String[] args) throws Exception {
        String protocolPath = CallsiteBindingProtocol.RESOURCE_PATH;
        System.out.println("RESOURCE_PATH=" + protocolPath);

        Path tempDir = Files.createTempDirectory("callsite-coordinate-probe");
        Path resourceFile = tempDir.resolve(protocolPath);
        Files.createDirectories(resourceFile.getParent());
        Files.writeString(resourceFile, CallsiteBindingProtocolCoordinateProbe.class.getName());

        boolean bound;
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {tempDir.toUri().toURL()}, CallsiteBindingProtocolCoordinateProbe.class.getClassLoader())) {
            LogRuntime.invokeCallsiteBinding(loader);
            bound = LogRuntime.getGlobalMaxMessageLength() == 4096;
        }

        System.out.println("BOUND_OK=" + bound);
        System.out.println("MAX_LENGTH=" + LogRuntime.getGlobalMaxMessageLength());
    }
}
