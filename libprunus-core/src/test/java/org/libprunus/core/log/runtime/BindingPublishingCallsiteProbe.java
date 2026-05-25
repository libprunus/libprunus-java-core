package org.libprunus.core.log.runtime;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BindingPublishingCallsiteProbe {

    private BindingPublishingCallsiteProbe() {
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
        Path tempDir = Files.createTempDirectory("callsite-bootstrap-probe");
        Path resourceDir = tempDir.resolve("META-INF/prunus/aot");
        Files.createDirectories(resourceDir);
        Files.writeString(
                resourceDir.resolve("runtime-binding-callsite"), BindingPublishingCallsiteProbe.class.getName());

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {tempDir.toUri().toURL()}, BindingPublishingCallsiteProbe.class.getClassLoader())) {
            LogRuntime.invokeCallsiteBinding(loader);
        }

        System.out.println("MAX_LENGTH=" + LogRuntime.getGlobalMaxMessageLength());
        System.out.println(
                "WHITELISTED_INTEGER=" + LogRuntime.globalConfigBinding().isWhitelisted(Integer.class));
        System.out.println(
                "WHITELISTED_STRING=" + LogRuntime.globalConfigBinding().isWhitelisted(String.class));
    }
}
