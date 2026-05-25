package org.libprunus.core.plugin.aot.log;

record FieldExtractorRef(
        String fieldName, String ownerInternalName, String methodName, String methodDescriptor, boolean isInterface) {}
