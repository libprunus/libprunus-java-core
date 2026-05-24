package org.libprunus.core.plugin.aot.log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class TypeNode {

    private final String binaryName;
    private final String packageName;
    private final String className;
    private final boolean isRegistryClass;
    private final boolean hasClassLevelIgnore;
    private final Family typeLevelFamily;
    private final Optional<MethodLoggingRule> methodRule;
    private final Optional<ToStringRule> toStringRule;
    private final List<MethodNode> declaredMethods;
    private final Map<String, MethodNode> declaredMethodIndex;
    private final List<FieldNode> declaredFields;
    private final List<FieldRenderSlot> toStringFieldChain;

    TypeNode(
            String binaryName,
            String packageName,
            String className,
            boolean isRegistryClass,
            boolean hasClassLevelIgnore,
            Family typeLevelFamily,
            Optional<MethodLoggingRule> methodRule,
            Optional<ToStringRule> toStringRule,
            List<MethodNode> declaredMethods,
            List<FieldNode> declaredFields,
            List<FieldRenderSlot> toStringFieldChain) {
        this.binaryName = binaryName;
        this.packageName = packageName;
        this.className = className;
        this.isRegistryClass = isRegistryClass;
        this.hasClassLevelIgnore = hasClassLevelIgnore;
        this.typeLevelFamily = typeLevelFamily;
        this.methodRule = methodRule;
        this.toStringRule = toStringRule;
        this.declaredMethods = List.copyOf(declaredMethods);
        Map<String, MethodNode> index = new HashMap<>(declaredMethods.size() * 2);
        for (MethodNode node : declaredMethods) {
            index.put(node.methodName() + node.methodDescriptor(), node);
        }
        this.declaredMethodIndex = Map.copyOf(index);
        this.declaredFields = List.copyOf(declaredFields);
        this.toStringFieldChain = List.copyOf(toStringFieldChain);
    }

    MethodNode findDeclaredMethod(String name, String descriptor) {
        return declaredMethodIndex.get(name + descriptor);
    }

    String binaryName() {
        return binaryName;
    }

    String packageName() {
        return packageName;
    }

    String className() {
        return className;
    }

    boolean isRegistryClass() {
        return isRegistryClass;
    }

    boolean hasClassLevelIgnore() {
        return hasClassLevelIgnore;
    }

    Family typeLevelFamily() {
        return typeLevelFamily;
    }

    Optional<MethodLoggingRule> methodRule() {
        return methodRule;
    }

    Optional<ToStringRule> toStringRule() {
        return toStringRule;
    }

    boolean methodEligible() {
        return methodEligibleOf(methodRule.isPresent(), isRegistryClass, hasClassLevelIgnore);
    }

    boolean toStringEligible() {
        return toStringEligibleOf(toStringRule.isPresent(), isRegistryClass, hasClassLevelIgnore);
    }

    static boolean methodEligibleOf(boolean methodRulePresent, boolean isRegistryClass, boolean hasClassLevelIgnore) {
        return methodRulePresent && !isRegistryClass && !hasClassLevelIgnore;
    }

    static boolean toStringEligibleOf(
            boolean toStringRulePresent, boolean isRegistryClass, boolean hasClassLevelIgnore) {
        return toStringRulePresent && !isRegistryClass && !hasClassLevelIgnore;
    }

    List<MethodNode> declaredMethods() {
        return declaredMethods;
    }

    List<FieldNode> declaredFields() {
        return declaredFields;
    }

    List<FieldRenderSlot> toStringFieldChain() {
        return toStringFieldChain;
    }
}
