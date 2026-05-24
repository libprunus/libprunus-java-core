package org.libprunus.core.plugin.aot.log;

record FieldNode(
        String declaringClassBinaryName,
        String name,
        String descriptor,
        int accessFlags,
        boolean toStringShapeEligible,
        Family family) {}
