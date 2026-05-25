package org.libprunus.core.log.runtime

import java.util.concurrent.atomic.AtomicReference
import org.libprunus.core.config.CoreRuntimeConfig

final class LogRuntimeTestSupport {

    private static volatile boolean legacyShimsInstalled = false

    static {
        installLegacyShims()
    }

    static void resetBinding() {
        installLegacyShims()

        LogRuntime.boundConfig = AbstractLogConfig.DEFAULT
        LogRuntime.boundMaxMessageLength = AbstractLogConfig.DEFAULT.maxMessageLength
        LogRuntime.bindingInitialized = false
        LogRuntime.ACTIVE_CONFIG_REF = new AtomicReference<>(
                new CoreRuntimeConfig(new LogRuntimeConfig(true)))
    }

    private static void installLegacyShims() {
        if (legacyShimsInstalled) {
            return
        }
        synchronized (LogRuntimeTestSupport) {
            if (legacyShimsInstalled) {
                return
            }

            CollectionRenderer.metaClass.render = { StringBuilder builder,
                                                    Object value,
                                                    int currentDepth,
                                                    int maxObjectLength,
                                                    int maxDepth,
                                                    int objectStartLength ->
                def context = new StringBuilderWithContext(builder)
                context.setMaxMessageLength(objectStartLength + normalizeBudget(maxObjectLength))
                delegate.render(context, value)
            }
            MapRenderer.metaClass.render = { StringBuilder builder,
                                             Object value,
                                             int currentDepth,
                                             int maxObjectLength,
                                             int maxDepth,
                                             int objectStartLength ->
                def context = new StringBuilderWithContext(builder)
                context.setMaxMessageLength(objectStartLength + normalizeBudget(maxObjectLength))
                delegate.render(context, value)
            }
            ObjectArrayRenderer.metaClass.render = { StringBuilder builder,
                                                     Object value,
                                                     int currentDepth,
                                                     int maxObjectLength,
                                                     int maxDepth,
                                                     int objectStartLength ->
                def context = new StringBuilderWithContext(builder)
                context.setMaxMessageLength(objectStartLength + normalizeBudget(maxObjectLength))
                delegate.render(context, value)
            }
            LoggableRenderer.metaClass.render = { StringBuilder builder,
                                                  Object value,
                                                  int currentDepth,
                                                  int maxObjectLength,
                                                  int maxDepth,
                                                  int objectStartLength ->
                def context = new StringBuilderWithContext(builder)
                context.setMaxMessageLength(objectStartLength + normalizeBudget(maxObjectLength))
                delegate.render(context, value)
            }
            IdentityRenderer.metaClass.render = { StringBuilder builder,
                                                  Object value,
                                                  int currentDepth,
                                                  int maxObjectLength,
                                                  int maxDepth,
                                                  int objectStartLength ->
                def context = new StringBuilderWithContext(builder)
                context.setMaxMessageLength(objectStartLength + normalizeBudget(maxObjectLength))
                delegate.render(context, value)
            }
            StringBuilderWithContext.metaClass.'static'.appendObjectTo = { StringBuilder builder,
                                                                            Object value,
                                                                            int currentDepth,
                                                                            int maxObjectLength,
                                                                            int maxDepth,
                                                                            int objectStartLength ->
                def context = new StringBuilderWithContext(builder)
                context.setMaxMessageLength(objectStartLength + normalizeBudget(maxObjectLength))
                context.appendObjectTo(value)
            }
            legacyShimsInstalled = true
        }
    }

    private static int normalizeBudget(int maxObjectLength) {
        return maxObjectLength < 0 ? Integer.MAX_VALUE : maxObjectLength
    }

    private LogRuntimeTestSupport() {
        throw new UnsupportedOperationException()
    }
}
