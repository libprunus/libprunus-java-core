package org.libprunus.core.plugin.aot.log;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;
import org.libprunus.core.annotation.AutomatedProcessingIgnore;

final class TypeNodeBuilder {

    private TypeNodeBuilder() {
        throw new UnsupportedOperationException();
    }

    static TypeNode build(RegistryRouteGraph graph, TypeDescription type) {
        String binaryName = type.getName();
        String packageName = type.getPackage() == null ? "" : type.getPackage().getName();
        String className = packageName.isEmpty() ? binaryName : binaryName.substring(packageName.length() + 1);
        boolean isRegistryClass = binaryName.equals(graph.metadata().registryBinaryName());
        boolean hasClassLevelIgnore =
                type.getDeclaredAnnotations().isAnnotationPresent(AutomatedProcessingIgnore.class);
        Family typeLevelFamily = FamilyDetector.detect(type.getDeclaredAnnotations(), binaryName);

        Optional<MethodLoggingRule> methodRule = matchMethodRule(graph, packageName, className, binaryName);
        Optional<ToStringRule> toStringRule = matchToStringRule(graph, packageName, className, binaryName);

        List<MethodNode> declaredMethods = buildDeclaredMethods(graph, type, typeLevelFamily);
        List<FieldNode> declaredFields = buildDeclaredFields(type, typeLevelFamily);

        boolean toStringEligible =
                TypeNode.toStringEligibleOf(toStringRule.isPresent(), isRegistryClass, hasClassLevelIgnore);
        List<FieldRenderSlot> toStringFieldChain = toStringEligible
                ? buildToStringFieldChain(graph, type, className, packageName, declaredFields)
                : List.of();

        return new TypeNode(
                binaryName,
                packageName,
                className,
                isRegistryClass,
                hasClassLevelIgnore,
                typeLevelFamily,
                methodRule,
                toStringRule,
                declaredMethods,
                declaredFields,
                toStringFieldChain);
    }

    private static Optional<MethodLoggingRule> matchMethodRule(
            RegistryRouteGraph graph, String packageName, String className, String binaryName) {
        MethodLoggingRule found = null;
        for (MethodLoggingRule rule : graph.methodLoggingRules()) {
            if (rule.matches(packageName, className)) {
                if (found != null) {
                    throw new IllegalStateException("Method owner route conflict for class " + binaryName
                            + ": multiple MethodLoggingProfile matches (" + found.routeId() + " and " + rule.routeId()
                            + ")");
                }
                found = rule;
            }
        }
        return Optional.ofNullable(found);
    }

    private static Optional<ToStringRule> matchToStringRule(
            RegistryRouteGraph graph, String packageName, String className, String binaryName) {
        ToStringRule found = null;
        for (ToStringRule rule : graph.toStringRules()) {
            if (rule.matches(packageName, className)) {
                if (found != null) {
                    throw new IllegalStateException("ToString owner route conflict for class " + binaryName
                            + ": multiple ToStringProfile matches (" + found.routeId() + " and " + rule.routeId()
                            + ")");
                }
                found = rule;
            }
        }
        return Optional.ofNullable(found);
    }

    private static List<MethodNode> buildDeclaredMethods(
            RegistryRouteGraph graph, TypeDescription type, Family typeLevelFamily) {
        List<MethodNode> nodes = new ArrayList<>();
        for (MethodDescription.InDefinedShape method : type.getDeclaredMethods()) {
            if (method.isConstructor() || method.isTypeInitializer()) {
                continue;
            }
            boolean shapeEligible = computeShapeEligible(method);
            boolean hasMethodLevelIgnore =
                    method.getDeclaredAnnotations().isAnnotationPresent(AutomatedProcessingIgnore.class);
            MethodFamilyResolver.OverrideChainAnalysis chainAnalysis =
                    MethodFamilyResolver.analyzeOverrideChain(method);
            boolean isOverride = chainAnalysis.isOverride();
            List<List<MethodDescription>> overrideLayers = chainAnalysis.layers();
            Family methodLevelFamily = FamilyDetector.detect(
                    method.getDeclaredAnnotations(), type.getName() + "#" + method.getName() + "()");
            Family effectiveMethodFamily = MethodFamilyResolver.resolveEffectiveMethodFamily(
                    method, typeLevelFamily, isOverride, overrideLayers, graph);
            int paramCount = method.getParameters().size();
            List<Family> parameterFamilies = new ArrayList<>(paramCount);
            for (int i = 0; i < paramCount; i++) {
                parameterFamilies.add(MethodFamilyResolver.resolveParameterFamily(
                        method, i, typeLevelFamily, isOverride, overrideLayers, graph));
            }
            Family returnFamily = MethodFamilyResolver.resolveReturnFamily(
                    method, typeLevelFamily, isOverride, overrideLayers, graph);
            boolean anyLiteralFamily = MethodFamilyResolver.anyParameterCarriesLiteralFamily(method);
            nodes.add(new MethodNode(
                    type.getName(),
                    method.getInternalName(),
                    method.getDescriptor(),
                    method.getModifiers(),
                    hasMethodLevelIgnore,
                    shapeEligible,
                    methodLevelFamily,
                    effectiveMethodFamily,
                    parameterFamilies,
                    returnFamily,
                    anyLiteralFamily));
        }
        return nodes;
    }

    private static boolean computeShapeEligible(MethodDescription method) {
        int modifiers = method.getModifiers();
        if (method.isStatic()) {
            return false;
        }
        if (!Modifier.isPublic(modifiers)) {
            return false;
        }
        // WHY: abstract/native methods have no Java body to instrument.
        if ((modifiers & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return false;
        }
        if (method.isBridge()) {
            return false;
        }
        if (ObjectMethodSignatures.isDeclaredOnObject(method.getInternalName(), method.getDescriptor())) {
            return false;
        }
        return true;
    }

    private static List<FieldNode> buildDeclaredFields(TypeDescription type, Family typeLevelFamily) {
        List<FieldNode> nodes = new ArrayList<>();
        for (FieldDescription.InDefinedShape field : type.getDeclaredFields()) {
            boolean shapeEligible = computeFieldShapeEligible(field);
            Family fieldLevel =
                    FamilyDetector.detect(field.getDeclaredAnnotations(), type.getName() + "#" + field.getName());
            Family resolved;
            if (fieldLevel != Family.NONE) {
                resolved = fieldLevel;
            } else if (typeLevelFamily != Family.NONE) {
                resolved = typeLevelFamily;
            } else {
                resolved = Family.PASS_THROUGH;
            }
            nodes.add(new FieldNode(
                    type.getName(),
                    field.getName(),
                    field.getDescriptor(),
                    field.getModifiers(),
                    shapeEligible,
                    resolved));
        }
        return nodes;
    }

    private static boolean computeFieldShapeEligible(FieldDescription field) {
        if (field.isStatic() || field.isSynthetic()) {
            return false;
        }
        if (Modifier.isTransient(field.getModifiers())) {
            return false;
        }
        if (field.getName().startsWith("$")) {
            return false;
        }
        return true;
    }

    private static List<FieldRenderSlot> buildToStringFieldChain(
            RegistryRouteGraph graph,
            TypeDescription rootType,
            String rootClassName,
            String rootPackageName,
            List<FieldNode> rootDeclaredFields) {
        List<FieldRenderSlot> slots = new ArrayList<>();
        for (FieldNode field : rootDeclaredFields) {
            if (!field.toStringShapeEligible()) {
                continue;
            }
            if (field.family() == Family.SUPPRESS) {
                continue;
            }
            slots.add(new FieldRenderSlot(
                    rootType.getInternalName(),
                    rootClassName,
                    field.name(),
                    field.descriptor(),
                    field.accessFlags(),
                    field.family(),
                    true));
        }
        // WHY: missing classpath jar -> stop chain at break point; partial inherited fields still rendered.
        try {
            TypeDescription.Generic generic = rootType.getSuperClass();
            while (generic != null) {
                TypeDescription supertype = generic.asErasure();
                if (supertype.getName().equals(Object.class.getName())) {
                    break;
                }
                TypeNode supertypeNode = graph.nodeOf(supertype);
                for (FieldNode field : supertypeNode.declaredFields()) {
                    if (!field.toStringShapeEligible()) {
                        continue;
                    }
                    if (field.family() == Family.SUPPRESS) {
                        continue;
                    }
                    if (!isFieldAccessibleFromRoot(field, supertypeNode.packageName(), rootPackageName)) {
                        continue;
                    }
                    slots.add(new FieldRenderSlot(
                            supertype.getInternalName(),
                            supertypeNode.className(),
                            field.name(),
                            field.descriptor(),
                            field.accessFlags(),
                            field.family(),
                            false));
                }
                generic = supertype.getSuperClass();
            }
        } catch (TypePool.Resolution.NoSuchTypeException ignored) {
        }
        return slots;
    }

    private static boolean isFieldAccessibleFromRoot(FieldNode field, String declaringPackage, String rootPackage) {
        int modifiers = field.accessFlags();
        if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
            return true;
        }
        if (Modifier.isPrivate(modifiers)) {
            return false;
        }
        return declaringPackage.equals(rootPackage);
    }
}
