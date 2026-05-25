package org.libprunus.core.log.runtime;

import org.libprunus.core.log.annotation.MaxMessageLength;

public final class LogRuntimeBindingProbe {

    private LogRuntimeBindingProbe() {
        throw new UnsupportedOperationException();
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "";

        if ("invalid-length".equals(mode)) {
            LogRuntime.initializeBinding(buildOverride(15));
            return;
        }

        if ("invalid-length-max".equals(mode)) {
            LogRuntime.initializeBinding(buildOverride(MaxMessageLength.MAX_VALUE + 1));
            return;
        }

        if ("invalid-length-state-check".equals(mode)) {
            try {
                LogRuntime.initializeBinding(buildOverride(15));
            } catch (IllegalArgumentException e) {
                printCaught(e);
            }
            printPostState();
            return;
        }

        if ("invalid-length-max-state-check".equals(mode)) {
            try {
                LogRuntime.initializeBinding(buildOverride(MaxMessageLength.MAX_VALUE + 1));
            } catch (IllegalArgumentException e) {
                printCaught(e);
            }
            printPostState();
            return;
        }

        if ("repeat-state-check".equals(mode)) {
            LogRuntime.initializeBinding(buildOverride(1024));
            try {
                LogRuntime.initializeBinding(buildSecondOverride(2048));
            } catch (IllegalStateException e) {
                printCaught(e);
            }
            printPostState();
            return;
        }

        System.out.println("BEFORE_MAX_LENGTH=" + LogRuntime.getGlobalMaxMessageLength());
        System.out.println(
                "BEFORE_WHITELISTED_OBJECT=" + LogRuntime.globalConfigBinding().isWhitelisted(Object.class));

        AbstractLogConfig override = buildOverride(1024);

        LogRuntime.initializeBinding(override);

        if ("repeat".equals(mode)) {
            LogRuntime.initializeBinding(override);
        }

        System.out.println("AFTER_MAX_LENGTH=" + LogRuntime.getGlobalMaxMessageLength());
        System.out.println(
                "AFTER_WHITELISTED_OBJECT=" + LogRuntime.globalConfigBinding().isWhitelisted(Object.class));
    }

    private static void printCaught(Throwable e) {
        System.out.println("CAUGHT_TYPE=" + e.getClass().getSimpleName());
        System.out.println("CAUGHT_MESSAGE=" + e.getMessage());
    }

    private static void printPostState() {
        System.out.println("POST_MAX_LENGTH=" + LogRuntime.getGlobalMaxMessageLength());
        System.out.println(
                "POST_WHITELISTED_OBJECT=" + LogRuntime.globalConfigBinding().isWhitelisted(Object.class));
        System.out.println("POST_IS_DEFAULT=" + (LogRuntime.globalConfigBinding() == AbstractLogConfig.DEFAULT));
    }

    private static AbstractLogConfig buildOverride(int maxObjectLength) {
        return new AbstractLogConfig() {
            @Override
            public int getMaxMessageLength() {
                return maxObjectLength;
            }

            @Override
            public boolean isWhitelisted(Class<?> type) {
                return type == Object.class;
            }
        };
    }

    private static AbstractLogConfig buildSecondOverride(int maxObjectLength) {
        return new AbstractLogConfig() {
            @Override
            public int getMaxMessageLength() {
                return maxObjectLength;
            }

            @Override
            public boolean isWhitelisted(Class<?> type) {
                return type == String.class;
            }
        };
    }
}
