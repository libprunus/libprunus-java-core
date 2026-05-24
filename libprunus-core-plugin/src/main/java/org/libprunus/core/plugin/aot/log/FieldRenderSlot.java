package org.libprunus.core.plugin.aot.log;

record FieldRenderSlot(
        String declaringClassInternalName,
        String declaringClassSimpleName,
        String name,
        String descriptor,
        int accessFlags,
        Family family,
        boolean isRootLayer) {}
