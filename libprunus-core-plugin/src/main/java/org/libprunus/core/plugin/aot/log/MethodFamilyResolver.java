package org.libprunus.core.plugin.aot.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;

final class MethodFamilyResolver {

    private MethodFamilyResolver() {
        throw new UnsupportedOperationException();
    }

    static OverrideChainAnalysis analyzeOverrideChain(MethodDescription method) {
        if (method.isStatic() || method.isPrivate() || method.isConstructor()) {
            return OverrideChainAnalysis.EMPTY;
        }
        MethodDescription.SignatureToken token = method.asSignatureToken();
        List<MethodDescription> allMatches = new ArrayList<>();
        Queue<TypeDefinition> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        safelyEnqueueSupertypes(method.getDeclaringType(), queue, visited);
        while (!queue.isEmpty()) {
            TypeDefinition supertype = queue.poll();
            for (MethodDescription candidate : supertype.getDeclaredMethods()) {
                if (candidate.isStatic() || candidate.isPrivate()) {
                    continue;
                }
                if (candidate.asSignatureToken().equals(token)) {
                    allMatches.add(candidate);
                }
            }
            safelyEnqueueSupertypes(supertype, queue, visited);
        }
        if (allMatches.isEmpty()) {
            return OverrideChainAnalysis.EMPTY;
        }
        List<List<MethodDescription>> layers = new ArrayList<>();
        // WHY: O(N^2) two-pointer topological partition; override chain breadth expected to stay below 10 in real code.
        while (!allMatches.isEmpty()) {
            List<MethodDescription> currentLayer = new ArrayList<>();
            for (MethodDescription m1 : allMatches) {
                TypeDescription t1 = m1.getDeclaringType().asErasure();
                boolean hasSubtype = false;
                for (MethodDescription m2 : allMatches) {
                    if (m1 == m2) {
                        continue;
                    }
                    TypeDescription t2 = m2.getDeclaringType().asErasure();
                    if (!t1.equals(t2) && t1.isAssignableFrom(t2)) {
                        hasSubtype = true;
                        break;
                    }
                }
                if (!hasSubtype) {
                    currentLayer.add(m1);
                }
            }
            layers.add(List.copyOf(currentLayer));
            allMatches.removeAll(currentLayer);
        }
        return new OverrideChainAnalysis(true, List.copyOf(layers));
    }

    record OverrideChainAnalysis(boolean isOverride, List<List<MethodDescription>> layers) {
        static final OverrideChainAnalysis EMPTY = new OverrideChainAnalysis(false, List.of());
    }

    static Family resolveEffectiveMethodFamily(
            MethodDescription method,
            Family declaringTypeFamily,
            boolean isOverride,
            List<List<MethodDescription>> overrideLayers,
            RegistryRouteGraph graph) {
        return resolveFamily(method, declaringTypeFamily, isOverride, overrideLayers, graph, ResolveTarget.METHOD, -1);
    }

    static Family resolveParameterFamily(
            MethodDescription method,
            int paramIndex,
            Family declaringTypeFamily,
            boolean isOverride,
            List<List<MethodDescription>> overrideLayers,
            RegistryRouteGraph graph) {
        return resolveFamily(
                method, declaringTypeFamily, isOverride, overrideLayers, graph, ResolveTarget.PARAMETER, paramIndex);
    }

    static Family resolveReturnFamily(
            MethodDescription method,
            Family declaringTypeFamily,
            boolean isOverride,
            List<List<MethodDescription>> overrideLayers,
            RegistryRouteGraph graph) {
        return resolveFamily(method, declaringTypeFamily, isOverride, overrideLayers, graph, ResolveTarget.RETURN, -1);
    }

    static boolean anyParameterCarriesLiteralFamily(MethodDescription method) {
        for (ParameterDescription param : method.getParameters()) {
            if (FamilyDetector.hasAnyFamily(param.getDeclaredAnnotations())) {
                return true;
            }
        }
        return false;
    }

    private static Family resolveFamily(
            MethodDescription method,
            Family declaringTypeFamily,
            boolean isOverride,
            List<List<MethodDescription>> overrideLayers,
            RegistryRouteGraph graph,
            ResolveTarget target,
            int paramIndex) {
        Family layer1 = layer1Closeness(method, declaringTypeFamily, target, paramIndex);
        if (layer1 != Family.NONE) {
            return layer1;
        }
        if (!isOverride) {
            return Family.NONE;
        }
        for (List<MethodDescription> layer : overrideLayers) {
            Family vote = layerVoteFromAncestors(layer, target, paramIndex, graph, method);
            if (vote != Family.NONE) {
                return vote;
            }
        }
        return Family.NONE;
    }

    private static Family layer1Closeness(
            MethodDescription method, Family declaringTypeFamily, ResolveTarget target, int paramIndex) {
        if (target == ResolveTarget.PARAMETER) {
            ParameterDescription param = method.getParameters().get(paramIndex);
            Family paramLevel =
                    FamilyDetector.detect(param.getDeclaredAnnotations(), describeParam(method, paramIndex));
            if (paramLevel != Family.NONE) {
                return paramLevel;
            }
        }
        Family methodLevel = FamilyDetector.detect(method.getDeclaredAnnotations(), describe(method));
        if (methodLevel != Family.NONE) {
            return methodLevel;
        }
        return declaringTypeFamily;
    }

    private enum ResolveTarget {
        METHOD,
        PARAMETER,
        RETURN
    }

    private record FamilyVote(Family family, Supplier<String> originDescriber) {}

    private static Family layerVoteFromAncestors(
            List<MethodDescription> layer,
            ResolveTarget target,
            int paramIndex,
            RegistryRouteGraph graph,
            MethodDescription subject) {
        List<FamilyVote> closerVotes = new ArrayList<>();
        for (MethodDescription ancestor : layer) {
            if (target == ResolveTarget.PARAMETER
                    && paramIndex < ancestor.getParameters().size()) {
                Family paramLevel = FamilyDetector.detect(
                        ancestor.getParameters().get(paramIndex).getDeclaredAnnotations(),
                        describeParam(ancestor, paramIndex));
                if (paramLevel != Family.NONE) {
                    MethodDescription captured = ancestor;
                    int capturedIndex = paramIndex;
                    closerVotes.add(new FamilyVote(paramLevel, () -> describeParam(captured, capturedIndex)));
                }
            }
        }
        if (!closerVotes.isEmpty()) {
            return mergeVotes(closerVotes, subject);
        }
        List<FamilyVote> methodLevelVotes = new ArrayList<>();
        for (MethodDescription ancestor : layer) {
            Family methodLevel = readAncestorMethodLevelFamily(graph, ancestor);
            if (methodLevel != Family.NONE) {
                MethodDescription captured = ancestor;
                methodLevelVotes.add(new FamilyVote(methodLevel, () -> describe(captured)));
            }
        }
        if (!methodLevelVotes.isEmpty()) {
            return mergeVotes(methodLevelVotes, subject);
        }
        List<FamilyVote> typeLevelVotes = new ArrayList<>();
        Set<String> seenTypes = new HashSet<>();
        for (MethodDescription ancestor : layer) {
            TypeDescription owner = ancestor.getDeclaringType().asErasure();
            if (!seenTypes.add(owner.getName())) {
                continue;
            }
            Family typeLevel = graph.nodeOf(owner).typeLevelFamily();
            if (typeLevel != Family.NONE) {
                String ownerName = owner.getName();
                typeLevelVotes.add(new FamilyVote(typeLevel, () -> ownerName));
            }
        }
        if (!typeLevelVotes.isEmpty()) {
            return mergeVotes(typeLevelVotes, subject);
        }
        return Family.NONE;
    }

    private static Family readAncestorMethodLevelFamily(RegistryRouteGraph graph, MethodDescription ancestor) {
        // WHY: ancestor cache hit is the hot path; fall back to direct detect when TypeNode has no
        // matching declared method (TypePool gaps, generics erasure mismatches).
        TypeDescription owner = ancestor.getDeclaringType().asErasure();
        MethodNode cached =
                graph.nodeOf(owner).findDeclaredMethod(ancestor.getInternalName(), ancestor.getDescriptor());
        if (cached != null) {
            return cached.methodLevelFamily();
        }
        return FamilyDetector.detect(ancestor.getDeclaredAnnotations(), describe(ancestor));
    }

    private static Family mergeVotes(List<FamilyVote> votes, MethodDescription subject) {
        Family first = votes.get(0).family();
        for (int i = 1; i < votes.size(); i++) {
            FamilyVote vote = votes.get(i);
            if (vote.family() != first) {
                throw new IllegalStateException("Same-layer multi-family conflict at "
                        + describe(subject)
                        + ": "
                        + votes.get(0).originDescriber().get() + " declares " + annotationLiteralOf(first)
                        + ", "
                        + vote.originDescriber().get() + " declares " + annotationLiteralOf(vote.family())
                        + " (@Sensitive / @DoNotLog / @DoLog are mutually exclusive"
                        + " at the same chain layer; configuration error)");
            }
        }
        return first;
    }

    private static String annotationLiteralOf(Family family) {
        return switch (family) {
            case MASK -> "@Sensitive";
            case SUPPRESS -> "@DoNotLog";
            case PASS_THROUGH -> "@DoLog";
            case NONE -> throw new IllegalStateException("unreachable: NONE family must not reach annotationLiteralOf");
        };
    }

    private static void safelyEnqueueSupertypes(TypeDefinition type, Queue<TypeDefinition> queue, Set<String> visited) {
        // WHY: missing classpath jar -> stop family BFS at break point; partial ancestor votes still merge.
        try {
            TypeDescription.Generic superClass = type.getSuperClass();
            if (superClass != null) {
                TypeDescription erasure = superClass.asErasure();
                if (!erasure.getName().equals(Object.class.getName()) && visited.add(erasure.getName())) {
                    queue.add(superClass);
                }
            }
            var interfaces = type.getInterfaces();
            for (int i = 0; i < interfaces.size(); i++) {
                var iface = interfaces.get(i);
                TypeDescription erasure = iface.asErasure();
                if (visited.add(erasure.getName())) {
                    queue.add(iface);
                }
            }
        } catch (TypePool.Resolution.NoSuchTypeException _) {
        }
    }

    private static String describe(MethodDescription method) {
        return method.getDeclaringType().asErasure().getName() + "#" + method.getName() + "()";
    }

    private static String describeParam(MethodDescription method, int paramIndex) {
        return describe(method) + " param[" + paramIndex + "]";
    }
}
