package org.libprunus.core.log.runtime;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Subprocess probe that writes the AOT callsite resource with extra whitespace around the class
 * name (trailing LF, trailing CR/LF, leading spaces) to verify {@link LogRuntime#invokeCallsiteBinding}
 * applies {@code String.strip()} before {@code Class.forName} on the resource content. Real-world
 * build tools and IDEs commonly emit trailing newlines or whitespace in {@code META-INF/} resource
 * files; the documented contract is that the runtime is tolerant of that.
 *
 * <p>Mode (selected by {@code args[0]}):
 * <ul>
 *   <li>{@code trailing-lf} — write {@code <className>\n}.
 *   <li>{@code trailing-crlf} — write {@code <className>\r\n}.
 *   <li>{@code leading-spaces} — write {@code "  " + <className> + "  "}.
 * </ul>
 *
 * <p>Output contract on success:
 * <ul>
 *   <li>{@code MODE=<mode>}
 *   <li>{@code MAX_LENGTH=2048} (probe-supplied binding sets this exact value)
 *   <li>{@code WHITELISTED_INTEGER=true}
 * </ul>
 */
public final class CallsiteResourceWhitespaceProbe {

    private CallsiteResourceWhitespaceProbe() {
        throw new UnsupportedOperationException();
    }

    public static void bind() {
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override
            public int getMaxMessageLength() {
                return 2048;
            }

            @Override
            public boolean isWhitelisted(Class<?> type) {
                return type == Integer.class;
            }
        });
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("PROBE_FAILED_NO_MODE");
            System.exit(1);
            return;
        }
        String mode = args[0];
        String className = CallsiteResourceWhitespaceProbe.class.getName();
        String resourceContent;
        switch (mode) {
            case "trailing-lf" -> resourceContent = className + "\n";
            case "trailing-crlf" -> resourceContent = className + "\r\n";
            case "leading-spaces" -> resourceContent = "  " + className + "  ";
            default -> {
                System.out.println("PROBE_FAILED_UNKNOWN_MODE_" + mode);
                System.exit(1);
                return;
            }
        }

        Path tempDir = Files.createTempDirectory("callsite-whitespace-probe");
        Path resourceFile = tempDir.resolve(CallsiteBindingProtocol.RESOURCE_PATH);
        Files.createDirectories(resourceFile.getParent());
        Files.writeString(resourceFile, resourceContent);

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {tempDir.toUri().toURL()}, CallsiteResourceWhitespaceProbe.class.getClassLoader())) {
            LogRuntime.invokeCallsiteBinding(loader);
        }

        System.out.println("MODE=" + mode);
        System.out.println("MAX_LENGTH=" + LogRuntime.getGlobalMaxMessageLength());
        System.out.println(
                "WHITELISTED_INTEGER=" + LogRuntime.globalConfigBinding().isWhitelisted(Integer.class));
    }
}
