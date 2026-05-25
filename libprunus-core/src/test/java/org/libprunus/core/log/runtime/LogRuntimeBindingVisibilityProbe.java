package org.libprunus.core.log.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class LogRuntimeBindingVisibilityProbe {

    private LogRuntimeBindingVisibilityProbe() {
        throw new UnsupportedOperationException();
    }

    public static void main(String[] args) throws Exception {
        boolean configVolatile = isVolatile("boundConfig");
        boolean maxLengthVolatile = isVolatile("boundMaxMessageLength");
        boolean activeConfigRefVolatile = isVolatile("ACTIVE_CONFIG_REF");
        boolean bindingInitializedVolatile = isVolatile("bindingInitialized");

        AbstractLogConfig override = new AbstractLogConfig() {
            @Override
            public int getMaxMessageLength() {
                return 1024;
            }

            @Override
            public boolean isWhitelisted(Class<?> type) {
                return type == String.class;
            }
        };

        LogRuntime.initializeBinding(override);

        boolean getterValuesMatch = LogRuntime.getGlobalMaxMessageLength() == 1024
                && LogRuntime.globalConfigBinding().isWhitelisted(String.class);

        if (!configVolatile
                || !maxLengthVolatile
                || !activeConfigRefVolatile
                || !bindingInitializedVolatile
                || !getterValuesMatch) {
            System.out.println("BINDING_VISIBILITY_PROBE_FAILED configVolatile=" + configVolatile
                    + " maxLengthVolatile=" + maxLengthVolatile
                    + " activeConfigRefVolatile=" + activeConfigRefVolatile
                    + " bindingInitializedVolatile=" + bindingInitializedVolatile
                    + " getterValuesMatch=" + getterValuesMatch);
            System.exit(2);
        }

        System.out.println("BINDING_VISIBILITY_OK configVolatile=" + configVolatile
                + " maxLengthVolatile=" + maxLengthVolatile
                + " activeConfigRefVolatile=" + activeConfigRefVolatile
                + " bindingInitializedVolatile=" + bindingInitializedVolatile
                + " getterValuesMatch=" + getterValuesMatch);
        System.exit(0);
    }

    private static boolean isVolatile(String fieldName) throws Exception {
        Field field = LogRuntime.class.getDeclaredField(fieldName);
        return Modifier.isVolatile(field.getModifiers());
    }
}
