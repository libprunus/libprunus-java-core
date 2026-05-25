package org.libprunus.core.plugin.aot.log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;

final class RegistryRouteGraph {

    private final RegistryMetadata metadata;
    private final List<MethodLoggingRule> methodLoggingRules;
    private final List<ToStringRule> toStringRules;
    private final TypeNodeFactory factory;
    private final Map<String, TypeNode> nodeCache;

    RegistryRouteGraph(
            RegistryMetadata metadata,
            List<MethodLoggingRule> methodLoggingRules,
            List<ToStringRule> toStringRules,
            TypeNodeFactory factory) {
        this.metadata = metadata;
        this.methodLoggingRules = List.copyOf(methodLoggingRules);
        this.toStringRules = List.copyOf(toStringRules);
        this.factory = factory;
        this.nodeCache = new ConcurrentHashMap<>();
    }

    RegistryMetadata metadata() {
        return metadata;
    }

    int globalMaxMessageLength() {
        return metadata.maxMessageLength();
    }

    List<String> directToStringWhitelist() {
        return metadata.directToStringWhitelist();
    }

    List<MethodLoggingRule> methodLoggingRules() {
        return methodLoggingRules;
    }

    List<ToStringRule> toStringRules() {
        return toStringRules;
    }

    TypeNode nodeOf(TypeDescription type) {
        // WHY: build path recurses via toString chain (root -> supertype nodes);
        // ConcurrentHashMap.computeIfAbsent rejects recursive updates on the same key set.
        String name = type.getName();
        TypeNode cached = nodeCache.get(name);
        if (cached != null) {
            return cached;
        }
        TypeNode built = factory.build(this, type);
        TypeNode existing = nodeCache.putIfAbsent(name, built);
        return existing != null ? existing : built;
    }

    String classNameOf(TypeDescription type) {
        return nodeOf(type).className();
    }

    boolean isRouteRelevant(TypeDescription type) {
        TypeNode node = nodeOf(type);
        return node.methodEligible() || node.toStringEligible();
    }

    boolean methodEligible(TypeDescription type) {
        return nodeOf(type).methodEligible();
    }

    boolean toStringEligible(TypeDescription type) {
        return nodeOf(type).toStringEligible();
    }

    MethodLoggingRule methodRuleFor(TypeDescription type) {
        return nodeOf(type)
                .methodRule()
                .orElseThrow(() -> new IllegalStateException("Method rule missing for " + type.getName()));
    }

    List<FieldRenderSlot> toStringFieldChain(TypeDescription type) {
        return nodeOf(type).toStringFieldChain();
    }

    boolean shouldEmitEnterExitFor(MethodNode methodNode) {
        if (methodNode == null) {
            return false;
        }
        if (!methodNode.methodLoggingShapeEligible() || methodNode.hasMethodLevelIgnore()) {
            return false;
        }
        return !isWholeMethodSkipApplicable(methodNode);
    }

    MethodNode findDeclaredMethodNode(TypeDescription declaringType, String name, String descriptor) {
        return nodeOf(declaringType).findDeclaredMethod(name, descriptor);
    }

    String declaringClassSimpleNameFor(MethodDescription method) {
        return classNameOf(method.getDeclaringType().asErasure());
    }

    MethodNode requireMethodNode(MethodDescription method) {
        TypeDescription declaringType = method.getDeclaringType().asErasure();
        MethodNode node = findMethodNode(declaringType, method);
        if (node == null) {
            throw new IllegalStateException("Method node missing for " + declaringType.getName() + "#"
                    + method.getInternalName() + method.getDescriptor());
        }
        return node;
    }

    private MethodNode findMethodNode(TypeDescription declaringType, MethodDescription method) {
        return nodeOf(declaringType).findDeclaredMethod(method.getInternalName(), method.getDescriptor());
    }

    private static boolean isWholeMethodSkipApplicable(MethodNode methodNode) {
        return methodNode.effectiveMethodFamily() == Family.SUPPRESS && !methodNode.anyParameterCarriesLiteralFamily();
    }

    @FunctionalInterface
    interface TypeNodeFactory {
        TypeNode build(RegistryRouteGraph graph, TypeDescription type);
    }
}
