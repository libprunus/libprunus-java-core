package org.libprunus.core.log.runtime;

sealed interface TypeRenderer
        permits IdentityRenderer,
                LoggableRenderer,
                ObjectArrayRenderer,
                CollectionRenderer,
                MapRenderer,
                NonSealedTypeRenderer {

    void render(StringBuilderWithContext context, Object value);
}
