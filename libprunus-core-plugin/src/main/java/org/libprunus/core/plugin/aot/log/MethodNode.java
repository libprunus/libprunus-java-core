package org.libprunus.core.plugin.aot.log;

import java.util.List;

final class MethodNode {

    private final String methodName;
    private final String methodDescriptor;
    private final boolean hasMethodLevelIgnore;
    private final boolean methodLoggingShapeEligible;
    private final Family methodLevelFamily;
    private final Family effectiveMethodFamily;
    private final List<Family> parameterFamilies;
    private final Family returnFamily;
    private final boolean anyParameterCarriesLiteralFamily;

    MethodNode(
            String methodName,
            String methodDescriptor,
            boolean hasMethodLevelIgnore,
            boolean methodLoggingShapeEligible,
            Family methodLevelFamily,
            Family effectiveMethodFamily,
            List<Family> parameterFamilies,
            Family returnFamily,
            boolean anyParameterCarriesLiteralFamily) {
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
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

    String methodName() {
        return methodName;
    }

    String methodDescriptor() {
        return methodDescriptor;
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
