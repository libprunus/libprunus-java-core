package org.libprunus.core.plugin.aot.log;

import java.util.List;

final class MethodNode {

    private final String declaringClassBinaryName;
    private final String methodName;
    private final String methodDescriptor;
    private final int accessFlags;
    private final boolean hasMethodLevelIgnore;
    private final boolean methodLoggingShapeEligible;
    private final Family methodLevelFamily;
    private final Family effectiveMethodFamily;
    private final List<Family> parameterFamilies;
    private final Family returnFamily;
    private final boolean anyParameterCarriesLiteralFamily;

    MethodNode(
            String declaringClassBinaryName,
            String methodName,
            String methodDescriptor,
            int accessFlags,
            boolean hasMethodLevelIgnore,
            boolean methodLoggingShapeEligible,
            Family methodLevelFamily,
            Family effectiveMethodFamily,
            List<Family> parameterFamilies,
            Family returnFamily,
            boolean anyParameterCarriesLiteralFamily) {
        this.declaringClassBinaryName = declaringClassBinaryName;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
        this.accessFlags = accessFlags;
        this.hasMethodLevelIgnore = hasMethodLevelIgnore;
        this.methodLoggingShapeEligible = methodLoggingShapeEligible;
        this.methodLevelFamily = methodLevelFamily;
        this.effectiveMethodFamily = effectiveMethodFamily;
        this.parameterFamilies = List.copyOf(parameterFamilies);
        this.returnFamily = returnFamily;
        this.anyParameterCarriesLiteralFamily = anyParameterCarriesLiteralFamily;
    }

    Family methodLevelFamily() {
        return methodLevelFamily;
    }

    String declaringClassBinaryName() {
        return declaringClassBinaryName;
    }

    String methodName() {
        return methodName;
    }

    String methodDescriptor() {
        return methodDescriptor;
    }

    int accessFlags() {
        return accessFlags;
    }

    boolean hasMethodLevelIgnore() {
        return hasMethodLevelIgnore;
    }

    boolean methodLoggingShapeEligible() {
        return methodLoggingShapeEligible;
    }

    Family effectiveMethodFamily() {
        return effectiveMethodFamily;
    }

    List<Family> parameterFamilies() {
        return parameterFamilies;
    }

    Family returnFamily() {
        return returnFamily;
    }

    boolean anyParameterCarriesLiteralFamily() {
        return anyParameterCarriesLiteralFamily;
    }
}
