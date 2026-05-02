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

        CompletableFuture<AotClassMetadata> existing = metadataCache.get(className);
        if (existing != null) {
            return unwrapJoin(existing);
        }
        CompletableFuture<AotClassMetadata> newFuture = new CompletableFuture<>();
        CompletableFuture<AotClassMetadata> winner = metadataCache.putIfAbsent(className, newFuture);
        if (winner != null) {
            return unwrapJoin(winner);
        }

        try {
            TypeDescription type = typeProvider.apply(className);
            AotClassMetadata parentMeta = null;
            if (type.getSuperClass() != null) {
                String parentName = type.getSuperClass().asErasure().getName();
                if (!Object.class.getName().equals(parentName)) {
                    // TODO Define whether superclass metadata traversal should share the same boundary as root-type
                    // eligibility. The current package gate prevents unbounded metadata expansion into dependency
                    // hierarchies, but it also drops inherited audit fields when parent types sit outside configured
                    // base packages. A dedicated superclass-metadata policy may preserve field visibility without
                    // broadening instrumentation scope.
                    parentMeta = resolveMetadata(parentName, typeProvider);
                }
            }
            AotClassMetadata metadata = buildMetadata(type, parentMeta);
            newFuture.complete(metadata);
            return metadata;
        } catch (Throwable throwable) {
            newFuture.completeExceptionally(throwable);
            metadataCache.remove(className, newFuture);
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
            if (field.isSynthetic() || field.isStatic() || Modifier.isTransient(field.getModifiers())) {
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
        String fieldName = field.getName();
        if (field.getDeclaringType().asErasure().isRecord()) {
            List<AccessorMethod> recordAccessors = methodsByName.getOrDefault(fieldName, List.of());
            for (AccessorMethod candidate : recordAccessors) {
                if (candidate.returnDescriptor().equals(field.getDescriptor())) {
                    return candidate.method();
                }
            }
        }
        if (fieldName.isEmpty()) {
            return null;
        }
        boolean booleanField = isBooleanField(field.getDescriptor());

        if (booleanField) {
            String isName = BeanPropertyNamingHelper.toGetterName(fieldName, true);
            List<AccessorMethod> isMethods = methodsByName.getOrDefault(isName, List.of());
            for (AccessorMethod candidate : isMethods) {
                if (isBooleanField(candidate.returnDescriptor())) {
                    return candidate.method();
                }
            }
        }

        String getterName = BeanPropertyNamingHelper.toGetterName(fieldName, false);
        List<AccessorMethod> getterMethods = methodsByName.getOrDefault(getterName, List.of());
        net.bytebuddy.description.method.MethodDescription.InDefinedShape exactMatch = null;
        for (AccessorMethod candidate : getterMethods) {
            net.bytebuddy.description.method.MethodDescription.InDefinedShape method = candidate.method();
            String returnDescriptor = candidate.returnDescriptor();

            if (returnDescriptor.equals(field.getDescriptor())) {
                return method;
            }
            if (exactMatch == null && !"V".equals(returnDescriptor)) {
                exactMatch = method;
            }
        }
        return exactMatch;
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
