package org.libprunus.core.plugin.aot.log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.enumeration.EnumerationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import org.libprunus.core.log.annotation.DirectToStringWhitelist;
import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MaxMessageLength;
import org.libprunus.core.log.annotation.MethodLoggingField;
import org.libprunus.core.log.annotation.MethodLoggingProfile;
import org.libprunus.core.log.annotation.MethodLoggingProfiles;
import org.libprunus.core.log.annotation.ToStringProfile;
import org.libprunus.core.log.annotation.ToStringProfiles;
import org.libprunus.core.log.runtime.LogLevel;

final class RegistryRouteGraphBuilder {

    private static final int MIN_OBJECT_LENGTH = MaxMessageLength.MIN_VALUE;
    private static final int MAX_MESSAGE_LENGTH_HARD_LIMIT = MaxMessageLength.MAX_VALUE;

    RegistryRouteGraph build(String registryClassName, ClassFileLocator classFileLocator, TypePool sharedTypePool) {
        TypeDescription registryType = resolveType(registryClassName, sharedTypePool);
        if (!registryType.getDeclaredAnnotations().isAnnotationPresent(LogRegistry.class)) {
            throw new IllegalStateException(
                    "AOT registry class must be annotated with @LogRegistry: " + registryType.getName());
        }
        AnnotationList annotations = registryType.getDeclaredAnnotations();
        int maxMessageLength = resolveProperty(
                annotations.ofType(MaxMessageLength.class),
                "value",
                Integer.class,
                v -> normalizeMaxMessageLength(v, registryType.getName()),
                MaxMessageLength.DEFAULT_VALUE);
        List<String> whitelist = resolveWhitelist(annotations.ofType(DirectToStringWhitelist.class));
        Map<String, FieldExtractorRef> fieldExtractors = resolveFieldExtractors(registryType);
        List<MethodLoggingRule> methodRules = resolveMethodRules(annotations, fieldExtractors);
        List<ToStringRule> toStringRules = resolveToStringRules(annotations);
        RegistryMetadata metadata = new RegistryMetadata(registryType.getName(), maxMessageLength, whitelist);
        return new RegistryRouteGraph(metadata, methodRules, toStringRules, TypeNodeBuilder::build);
    }

    private static TypeDescription resolveType(String className, TypePool typePool) {
        try {
            TypePool.Resolution resolution = typePool.describe(className);
            if (!resolution.isResolved()) {
                throw new IllegalStateException("AOT registry class not found: " + className);
            }
            return resolution.resolve();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("AOT registry class not found: " + className, exception);
        }
    }

    private static int normalizeMaxMessageLength(int value, String ownerName) {
        if (value < 0) {
            throw new IllegalStateException("@MaxMessageLength value must be >= 0 on " + ownerName + ": " + value);
        }
        if (value > MAX_MESSAGE_LENGTH_HARD_LIMIT) {
            throw new IllegalStateException("@MaxMessageLength value must be <= " + MAX_MESSAGE_LENGTH_HARD_LIMIT
                    + " on " + ownerName + ": " + value);
        }
        return Math.max(MIN_OBJECT_LENGTH, value);
    }

    private static List<String> resolveWhitelist(AnnotationDescription.Loadable<DirectToStringWhitelist> annotation) {
        if (annotation == null) {
            return RuntimeBindingAbi.CORE_BUILTIN_WHITELIST;
        }
        TypeDescription[] values = annotation.getValue("value").resolve(TypeDescription[].class);
        LinkedHashSet<String> collected = new LinkedHashSet<>();
        for (TypeDescription value : values) {
            if (value != null) {
                collected.add(value.getName());
            }
        }
        if (collected.isEmpty()) {
            return List.of();
        }
        return List.copyOf(collected);
    }

    private static <T, R> R resolveProperty(
            AnnotationDescription annotation, String property, Class<T> type, Function<T, R> mapper, R defaultValue) {
        if (annotation == null) {
            return defaultValue;
        }
        T rawValue = annotation.getValue(property).resolve(type);
        return mapper.apply(rawValue);
    }

    private static List<String> filterBlankEntries(String[] entries) {
        ArrayList<String> cleaned = new ArrayList<>(entries.length);
        for (String entry : entries) {
            if (entry != null) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    cleaned.add(trimmed);
                }
            }
        }
        return cleaned;
    }

    private static void requireMethodLoggingFieldShape(
            String shapeRequirement, TypeDescription registryType, MethodDescription method) {
        throw new IllegalStateException("@MethodLoggingField method " + shapeRequirement + ": " + registryType.getName()
                + "#" + method.getName());
    }

    static Map<String, FieldExtractorRef> resolveFieldExtractors(TypeDescription registryType) {
        MethodList<MethodDescription.InDefinedShape> methods = registryType.getDeclaredMethods();
        Map<String, FieldExtractorRef> extractors = new LinkedHashMap<>();
        for (MethodDescription.InDefinedShape method : methods) {
            AnnotationDescription.Loadable<MethodLoggingField> annotation =
                    method.getDeclaredAnnotations().ofType(MethodLoggingField.class);
            if (annotation == null) {
                continue;
            }
            if (!registryType.isPublic()) {
                throw new IllegalStateException(
                        "AOT registry class defining @MethodLoggingField must be public to allow cross-package invocations: "
                                + registryType.getName());
            }
            String fieldName = annotation.getValue("value").resolve(String.class);
            if (!method.isPublic()) {
                requireMethodLoggingFieldShape("must be public", registryType, method);
            }
            if (!method.isStatic()) {
                requireMethodLoggingFieldShape("must be static", registryType, method);
            }
            if (!method.getParameters().isEmpty()) {
                requireMethodLoggingFieldShape("must have no parameters", registryType, method);
            }
            if (method.getReturnType().represents(void.class)) {
                requireMethodLoggingFieldShape("must not return void", registryType, method);
            }
            if (extractors.containsKey(fieldName)) {
                throw new IllegalStateException(
                        "Duplicate @MethodLoggingField name '" + fieldName + "' in " + registryType.getName());
            }
            extractors.put(
                    fieldName,
                    new FieldExtractorRef(
                            fieldName,
                            registryType.getInternalName(),
                            method.getName(),
                            method.getDescriptor(),
                            registryType.isInterface()));
        }
        return extractors;
    }

    private static List<MethodLoggingRule> resolveMethodRules(
            AnnotationList annotations, Map<String, FieldExtractorRef> allFieldExtractors) {
        AnnotationDescription[] profileValues = collectAnnotations(
                annotations, MethodLoggingProfile.class.getName(), MethodLoggingProfiles.class.getName());
        if (profileValues.length == 0) {
            return List.of();
        }
        ArrayList<MethodLoggingRule> rules = new ArrayList<>(profileValues.length);
        for (int index = 0; index < profileValues.length; index++) {
            AnnotationDescription profile = profileValues[index];
            List<String> includePackages =
                    filterBlankEntries(profile.getValue("includePackages").resolve(String[].class));
            List<String> excludePackages =
                    filterBlankEntries(profile.getValue("excludePackages").resolve(String[].class));
            List<String> includeClassSuffixes =
                    filterBlankEntries(profile.getValue("includeClassSuffixes").resolve(String[].class));
            String[] fieldNames = profile.getValue("fields").resolve(String[].class);
            List<FieldExtractorRef> extractors = resolveProfileFields(fieldNames, allFieldExtractors, index);
            LogLevel enterLevel = LogLevel.valueOf(profile.getValue("entryLevel")
                    .resolve(EnumerationDescription.class)
                    .getValue());
            LogLevel exitLevel = LogLevel.valueOf(profile.getValue("exitLevel")
                    .resolve(EnumerationDescription.class)
                    .getValue());
            rules.add(new MethodLoggingRule(
                    "method-route-" + index,
                    includePackages,
                    excludePackages,
                    includeClassSuffixes,
                    enterLevel,
                    exitLevel,
                    extractors));
        }
        return List.copyOf(rules);
    }

    private static List<ToStringRule> resolveToStringRules(AnnotationList annotations) {
        AnnotationDescription[] profileValues =
                collectAnnotations(annotations, ToStringProfile.class.getName(), ToStringProfiles.class.getName());
        if (profileValues.length == 0) {
            return List.of();
        }
        ArrayList<ToStringRule> rules = new ArrayList<>(profileValues.length);
        for (int index = 0; index < profileValues.length; index++) {
            AnnotationDescription profile = profileValues[index];
            List<String> includePackages =
                    filterBlankEntries(profile.getValue("includePackages").resolve(String[].class));
            List<String> excludePackages =
                    filterBlankEntries(profile.getValue("excludePackages").resolve(String[].class));
            List<String> includeClassSuffixes =
                    filterBlankEntries(profile.getValue("includeClassSuffixes").resolve(String[].class));
            rules.add(new ToStringRule("pojo-route-" + index, includePackages, excludePackages, includeClassSuffixes));
        }
        return List.copyOf(rules);
    }

    private static AnnotationDescription[] collectAnnotations(
            AnnotationList annotations, String singleName, String containerName) {
        ArrayList<AnnotationDescription> collected = new ArrayList<>();
        for (AnnotationDescription annotation : annotations) {
            String name = annotation.getAnnotationType().asErasure().getName();
            if (singleName.equals(name)) {
                collected.add(annotation);
                continue;
            }
            if (containerName.equals(name)) {
                AnnotationDescription[] nested = annotation.getValue("value").resolve(AnnotationDescription[].class);
                for (AnnotationDescription entry : nested) {
                    collected.add(entry);
                }
            }
        }
        return collected.toArray(AnnotationDescription[]::new);
    }

    private static List<FieldExtractorRef> resolveProfileFields(
            String[] fieldNames, Map<String, FieldExtractorRef> allFieldExtractors, int profileIndex) {
        if (fieldNames.length == 0) {
            return List.of();
        }
        LinkedHashSet<String> uniqueNames = new LinkedHashSet<>();
        for (String name : fieldNames) {
            uniqueNames.add(name);
        }
        List<FieldExtractorRef> result = new ArrayList<>(uniqueNames.size());
        for (String fieldName : uniqueNames) {
            FieldExtractorRef extractor = allFieldExtractors.get(fieldName);
            if (extractor == null) {
                throw new IllegalStateException("Profile-"
                        + profileIndex
                        + " references unknown @MethodLoggingField name '"
                        + fieldName
                        + "'. Available: "
                        + allFieldExtractors.keySet());
            }
            result.add(extractor);
        }
        return result;
    }
}
