package org.libprunus.core.plugin.aot.log;

final class WeavingInternalNames {

    static final String AOT_RUNTIME_INTERNAL_NAME = "org/libprunus/core/log/runtime/LogRuntime";
    static final String AOT_LOGGABLE_BINARY_NAME = "org.libprunus.core.log.runtime.Loggable";
    static final String STRING_BUILDER_POOL_INTERNAL_NAME = "org/libprunus/core/log/runtime/StringBuilderPool";

    static final String AOT_RENDER_METHOD = "_libprunus_render";
    static final String AOT_RENDER_DESCRIPTOR = "(" + AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_DESCRIPTOR + ")V";

    static final String MASK_SENTINEL = "***";
    static final String SYNTHETIC_ENTER_PREFIX = "$lp$enter$";
    static final String SYNTHETIC_EXIT_PREFIX = "$lp$exit$";
    static final String SYNTHETIC_ENRICH_METHOD = "$lp$enrich";

    private WeavingInternalNames() {
        throw new UnsupportedOperationException();
    }

    static boolean isSyntheticMethodName(String name) {
        return name.startsWith(SYNTHETIC_ENTER_PREFIX)
                || name.startsWith(SYNTHETIC_EXIT_PREFIX)
                || SYNTHETIC_ENRICH_METHOD.equals(name);
    }
}
