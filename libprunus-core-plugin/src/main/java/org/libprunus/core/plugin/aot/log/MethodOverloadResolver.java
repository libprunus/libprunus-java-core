package org.libprunus.core.plugin.aot.log;

import java.util.HashSet;
import java.util.Set;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;

final class MethodOverloadResolver {

    private MethodOverloadResolver() {
        throw new UnsupportedOperationException();
    }

    static Set<String> detectOverloadedNames(RegistryRouteGraph graph, TypeDescription instrumentedType) {
        Set<String> seen = null;
        Set<String> overloaded = null;
        for (MethodNode node : graph.nodeOf(instrumentedType).declaredMethods()) {
            if (!node.methodLoggingShapeEligible() || node.hasMethodLevelIgnore()) {
                continue;
            }
            if (seen == null) {
                seen = new HashSet<>();
            }
            if (!seen.add(node.methodName())) {
                if (overloaded == null) {
                    overloaded = new HashSet<>();
                }
                overloaded.add(node.methodName());
            }
        }
        return overloaded == null ? Set.of() : overloaded;
    }

    static String buildOverloadSuffix(MethodDescription method) {
        StringBuilder sb = new StringBuilder();
        for (ParameterDescription param : method.getParameters()) {
            sb.append('$');
            TypeDescription erasure = param.getType().asErasure();
            sb.append(sanitizeTypeName(erasure.getName()));
        }
        if (method.getParameters().isEmpty()) {
            sb.append("$void");
        }
        sb.append('$');
        sb.append(sanitizeTypeName(method.getReturnType().asErasure().getName()));
        return sb.toString();
    }

    private static String sanitizeTypeName(String typeName) {
        StringBuilder sb = new StringBuilder(typeName.length());
        for (int i = 0; i < typeName.length(); i++) {
            char c = typeName.charAt(i);
            sb.append(
                    switch (c) {
                        case '.', '[', ']', ';', '$' -> '_';
                        default -> c;
                    });
        }
        return sb.toString();
    }
}
