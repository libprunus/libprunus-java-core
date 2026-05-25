package org.libprunus.core.plugin.aot.log;

import java.util.HashSet;
import java.util.Set;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;

final class ObjectMethodSignatures {

    private static final Set<String> SIGNATURES = buildSignatures();

    private ObjectMethodSignatures() {
        throw new UnsupportedOperationException();
    }

    static boolean isDeclaredOnObject(String name, String descriptor) {
        return SIGNATURES.contains(name + descriptor);
    }

    private static Set<String> buildSignatures() {
        TypeDescription objectType = TypeDescription.ForLoadedType.of(Object.class);
        Set<String> collected = new HashSet<>();
        for (MethodDescription method : objectType.getDeclaredMethods()) {
            collected.add(method.getInternalName() + method.getDescriptor());
        }
        return Set.copyOf(collected);
    }
}
