package org.libprunus.core.plugin.aot;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.enumeration.EnumerationDescription;
import net.bytebuddy.description.field.FieldDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import org.libprunus.core.log.annotation.MaskStrategy;
import org.libprunus.core.log.annotation.Sensitive;

public final class AotCompileContext {

    private final boolean handleInaccessibleField;
    private final PackagePrefixMatcher baseMatcher;

    public AotCompileContext() {
        this(false, List.of());
    }

    public AotCompileContext(boolean handleInaccessibleField) {
        this(handleInaccessibleField, List.of());
    }

    public AotCompileContext(boolean handleInaccessibleField, List<String> basePackages) {
        this.handleInaccessibleField = handleInaccessibleField;
        this.baseMatcher = new PackagePrefixMatcher(basePackages, true);
    }

    public record AotFieldMetadata(
            String ownerInternalName,
            String name,
            String descriptor,
            boolean masked,
            int accessFlags,
            String accessorOwnerInternalName,
            String accessorName,
            String accessorDescriptor,
            int accessorAccessFlags,
            boolean renderThroughAccessor,
            boolean inaccessibleByPolicy) {

        public boolean isPublic() {
            return Modifier.isPublic(accessFlags);
        }

        public boolean isProtected() {
            return Modifier.isProtected(accessFlags);
        }

        public boolean isPrivate() {
            return Modifier.isPrivate(accessFlags);
        }

        public boolean isStatic() {
            return Modifier.isStatic(accessFlags);
        }

        public boolean isPackagePrivate() {
            return !isPublic() && !isProtected() && !isPrivate();
        }

        public boolean hasAccessor() {
            return accessorName != null;
        }

        public boolean accessorIsPublic() {
            return Modifier.isPublic(accessorAccessFlags);
        }

        public boolean accessorIsProtected() {
            return Modifier.isProtected(accessorAccessFlags);
        }

        public boolean accessorIsPrivate() {
            return Modifier.isPrivate(accessorAccessFlags);
        }

        public boolean accessorIsPackagePrivate() {
            return hasAccessor() && !accessorIsPublic() && !accessorIsProtected() && !accessorIsPrivate();
        }

        public String renderingDescriptor() {
            if (inaccessibleByPolicy) {
                return "Ljava/lang/String;";
            }
            if (renderThroughAccessor) {
                return accessorDescriptor.substring(accessorDescriptor.indexOf(')') + 1);
            }
            return descriptor;
        }

        public AotFieldMetadata asAccessorBridge() {
            return new AotFieldMetadata(
                    ownerInternalName,
                    name,
                    descriptor,
                    masked,
                    accessFlags,
                    accessorOwnerInternalName,
                    accessorName,
                    accessorDescriptor,
                    accessorAccessFlags,
                    true,
                    false);
        }

        public AotFieldMetadata asInaccessiblePlaceholder() {
            return new AotFieldMetadata(
                    ownerInternalName,
                    name,
                    descriptor,
                    masked,
                    accessFlags,
                    accessorOwnerInternalName,
                    accessorName,
                    accessorDescriptor,
                    accessorAccessFlags,
                    false,
                    true);
        }
    }

    public record AotClassMetadata(
            String internalName, String simpleName, List<AotFieldMetadata> declaredFields, AotClassMetadata parent) {}

    private final Map<String, CompletableFuture<AotClassMetadata>> metadataCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> matchedPluginMasks = new ConcurrentHashMap<>();
    private final Map<ClassFileLocator, LazyTypePool> typePoolsByLocator = new ConcurrentHashMap<>();

    public boolean hasSuffix(String simpleName, String suffix) {
        return simpleName.endsWith(suffix);
    }

    public AotClassMetadata resolveMetadata(String className, Function<String, TypeDescription> typeProvider) {
        if (!baseMatcher.matches(className)) {
            return null;
        }

        CompletableFuture<AotClassMetadata> cached = metadataCache.get(className);
        if (cached != null) {
            return unwrapJoin(cached);
        }

        CompletableFuture<AotClassMetadata> future = new CompletableFuture<>();
        CompletableFuture<AotClassMetadata> existing = metadataCache.putIfAbsent(className, future);
        if (existing != null) {
            return unwrapJoin(existing);
        }

        try {
            TypeDescription type = typeProvider.apply(className);
            AotClassMetadata parentMeta = null;
            if (type.getSuperClass() != null) {
                String parentName = type.getSuperClass().asErasure().getName();
                if (!Object.class.getName().equals(parentName)) {
                    parentMeta = resolveMetadata(parentName, typeProvider);
                }
            }
            AotClassMetadata metadata = buildMetadata(type, parentMeta);
            future.complete(metadata);
            return metadata;
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
            metadataCache.remove(className, future);
            throw throwable;
        }
    }

    private static <T> T unwrapJoin(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Unexpected checked exception in resolveMetadata", cause);
        }
    }

    public int computeMaskIfAbsent(String className, Function<String, Integer> loader) {
        return matchedPluginMasks.computeIfAbsent(className, loader);
    }

    public TypePool sharedTypePool(ClassFileLocator locator) {
        return typePoolsByLocator
                .computeIfAbsent(locator, unused -> new LazyTypePool())
                .get(locator);
    }

    public void clear() {
        metadataCache.clear();
        matchedPluginMasks.clear();
        typePoolsByLocator.clear();
    }

    private static final class LazyTypePool {

        private volatile TypePool instance;

        private TypePool get(ClassFileLocator locator) {
            TypePool resolved = instance;
            if (resolved != null) {
                return resolved;
            }
            synchronized (this) {
                resolved = instance;
                if (resolved == null) {
                    resolved = TypePool.Default.of(locator);
                    instance = resolved;
                }
            }
            return resolved;
        }
    }

    private AotClassMetadata buildMetadata(TypeDescription type, AotClassMetadata parent) {
        boolean classMasked = type.getDeclaredAnnotations().isAnnotationPresent(Sensitive.class);
        String internalName = type.getInternalName();
        List<AotFieldMetadata> fields = new ArrayList<>();
        Map<String, List<AccessorMethod>> methodsByName = indexAccessorMethods(type);

        for (InDefinedShape field : type.getDeclaredFields()) {
            if (field.isSynthetic() || field.isStatic()) {
                continue;
            }

            boolean masked = classMasked;
            var sensitiveAnnotation = field.getDeclaredAnnotations().ofType(Sensitive.class);
            if (isAllMaskStrategy(sensitiveAnnotation)) {
                masked = true;
            } else if (sensitiveAnnotation != null) {
                masked = false;
            }

            net.bytebuddy.description.method.MethodDescription.InDefinedShape accessor =
                    resolveAccessor(methodsByName, field);
            boolean inaccessibleByPolicy =
                    !handleInaccessibleField && Modifier.isPrivate(field.getModifiers()) && accessor == null;
            fields.add(new AotFieldMetadata(
                    internalName,
                    field.getName(),
                    field.getDescriptor(),
                    masked,
                    field.getModifiers(),
                    accessor == null
                            ? null
                            : accessor.getDeclaringType().asErasure().getInternalName(),
                    accessor == null ? null : accessor.getName(),
                    accessor == null ? null : accessor.getDescriptor(),
                    accessor == null ? 0 : accessor.getModifiers(),
                    false,
                    inaccessibleByPolicy));
        }

        return new AotClassMetadata(internalName, type.getSimpleName(), fields, parent);
    }

    private static Map<String, List<AccessorMethod>> indexAccessorMethods(TypeDescription type) {
        Map<String, List<AccessorMethod>> methodsByName = new HashMap<>();
        int order = 0;
        for (net.bytebuddy.description.method.MethodDescription.InDefinedShape method : type.getDeclaredMethods()) {
            if (method.isStatic()
                    || method.isSynthetic()
                    || !method.getParameters().isEmpty()
                    || method.isPrivate()) {
                order++;
                continue;
            }
            methodsByName
                    .computeIfAbsent(method.getName(), unused -> new ArrayList<>(1))
                    .add(new AccessorMethod(order, method));
            order++;
        }
        return methodsByName;
    }

    private static boolean isAllMaskStrategy(AnnotationDescription annotation) {
        if (annotation == null) {
            return false;
        }
        EnumerationDescription strategy = annotation.getValue("strategy").resolve(EnumerationDescription.class);
        return MaskStrategy.ALL.name().equals(strategy.getValue());
    }

    private net.bytebuddy.description.method.MethodDescription.InDefinedShape resolveAccessor(
            Map<String, List<AccessorMethod>> methodsByName, InDefinedShape field) {
        if (field.getDeclaringType().asErasure().isRecord()) {
            List<AccessorMethod> recordAccessors = methodsByName.getOrDefault(field.getName(), List.of());
            for (AccessorMethod candidate : recordAccessors) {
                if (candidate.returnDescriptor().equals(field.getDescriptor())) {
                    return candidate.method();
                }
            }
        }
        String fieldName = field.getName();
        boolean booleanField = isBooleanField(field.getDescriptor());
        List<AccessorMethod> getterMethods = findAccessorCandidates(methodsByName, "get", fieldName);
        List<AccessorMethod> isMethods =
                booleanField ? findAccessorCandidates(methodsByName, "is", fieldName) : List.of();
        net.bytebuddy.description.method.MethodDescription.InDefinedShape exactMatch = null;
        int getterIndex = 0;
        int isIndex = 0;

        while (getterIndex < getterMethods.size() || isIndex < isMethods.size()) {
            boolean pickGetter = isIndex >= isMethods.size()
                    || (getterIndex < getterMethods.size()
                            && getterMethods.get(getterIndex).order()
                                    <= isMethods.get(isIndex).order());

            AccessorMethod candidate = pickGetter ? getterMethods.get(getterIndex++) : isMethods.get(isIndex++);
            net.bytebuddy.description.method.MethodDescription.InDefinedShape method = candidate.method();
            String returnDescriptor = candidate.returnDescriptor();

            if (pickGetter) {
                if (returnDescriptor.equals(field.getDescriptor())) {
                    return method;
                }
                if (exactMatch == null && !"V".equals(returnDescriptor)) {
                    exactMatch = method;
                }
                continue;
            }

            if (isBooleanField(returnDescriptor)) {
                return method;
            }
        }
        return exactMatch;
    }

    private static List<AccessorMethod> findAccessorCandidates(
            Map<String, List<AccessorMethod>> methodsByName, String prefix, String fieldName) {
        if (fieldName.isEmpty()) {
            return List.of();
        }
        for (Map.Entry<String, List<AccessorMethod>> entry : methodsByName.entrySet()) {
            if (matchesAccessorName(entry.getKey(), prefix, fieldName)) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    private static boolean matchesAccessorName(String methodName, String prefix, String fieldName) {
        int prefixLen = prefix.length();
        if (methodName.length() != prefixLen + fieldName.length()) {
            return false;
        }
        for (int i = 0; i < prefixLen; i++) {
            if (methodName.charAt(i) != prefix.charAt(i)) {
                return false;
            }
        }
        char firstChar = fieldName.charAt(0);
        if (methodName.charAt(prefixLen) != Character.toUpperCase(firstChar)) {
            return false;
        }
        for (int i = 1; i < fieldName.length(); i++) {
            if (methodName.charAt(prefixLen + i) != fieldName.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private record AccessorMethod(int order, net.bytebuddy.description.method.MethodDescription.InDefinedShape method) {
        private String returnDescriptor() {
            return method.getReturnType().asErasure().getDescriptor();
        }
    }

    private static boolean isBooleanField(String descriptor) {
        return "Z".equals(descriptor);
    }
}
