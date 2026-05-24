package org.libprunus.core.plugin.aot.log;

import java.util.Arrays;
import java.util.List;
import org.libprunus.core.log.annotation.DirectToStringWhitelist;

final class RuntimeBindingAbi {

    static final String AOT_RUNTIME_INTERNAL_NAME = "org/libprunus/core/log/runtime/LogRuntime";
    static final String RUNTIME_BUILD_WHITELIST_CACHE_METHOD = "buildWhitelistCache";
    static final String RUNTIME_BUILD_WHITELIST_CACHE_DESCRIPTOR = "([Ljava/lang/String;)Ljava/lang/ClassValue;";
    static final String RUNTIME_IS_WHITELISTED_CACHED_METHOD = "isWhitelistedCached";
    static final String RUNTIME_IS_WHITELISTED_CACHED_DESCRIPTOR = "(Ljava/lang/Class;Ljava/lang/ClassValue;)Z";
    static final String INITIALIZE_BINDING_METHOD = "initializeBinding";

    /**
     * Shared between U-5 ({@link DirectToStringWhitelist} default fallback per D-17) and U-7 (binding class generation
     * plus whitelist aggregation start). Single source of truth derived from {@link DirectToStringWhitelist#CORE_BUILTIN}.
     */
    static final List<String> CORE_BUILTIN_WHITELIST = Arrays.stream(DirectToStringWhitelist.CORE_BUILTIN)
            .map(Class::getName)
            .toList();

    private RuntimeBindingAbi() {
        throw new UnsupportedOperationException();
    }
}
