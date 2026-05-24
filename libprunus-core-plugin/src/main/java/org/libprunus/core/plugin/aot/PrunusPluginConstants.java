package org.libprunus.core.plugin.aot;

import org.libprunus.core.log.runtime.CallsiteBindingProtocol;

public final class PrunusPluginConstants {

    private static final String LOG_ANNOTATION_PACKAGE = "org.libprunus.core.log.annotation.";

    // === Section 1: Whitelist resource paths ===
    public static final String WHITELIST_RESOURCE_DIR = "META-INF/prunus/";
    public static final String WHITELIST_RESOURCE_PATH =
            WHITELIST_RESOURCE_DIR + "org.libprunus.core.log.annotation.LogRegistry.whitelist";

    // === Section 2: Generated AOT class naming ===
    public static final String GENERATED_AOT_PACKAGE = "org.libprunus.aot.generated";
    public static final String GENERATED_AOT_BINDING_IMPL_SIMPLE_NAME = "LogConfigBindingImpl";
    public static final String GENERATED_AOT_RUNTIME_CALLSITE_SIMPLE_NAME = "RuntimeBindingCallsite";

    // === Section 3: Gradle task names ===
    public static final String GENERATE_AOT_BINDING_TASK = "generateAotBinding";
    public static final String RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK = "resolveLogConfigProviderConflict";
    public static final String VERIFY_BOOT_JAR_PROVIDER_BINDING_TASK = "verifyBootJarProviderBinding";
    public static final String VERIFY_SHADOW_JAR_PROVIDER_BINDING_TASK = "verifyShadowJarProviderBinding";
    public static final String GENERATE_LIBRARY_WHITELIST_TASK = "generateLibraryWhitelist";

    // === Section 4: Generated output directories ===
    public static final String GENERATED_LIBRARY_WHITELIST_DIR = "generated/sources/aot-whitelist/main/";
    public static final String GENERATED_AOT_BINDING_DIR = "generated/classes/aot-binding/main/";

    // === Section 5: Gradle property keys + task @Input property names ===
    public static final String AOT_PROVIDER_BINDING_CLASS_PROPERTY = "prunus.aot.provider.bindingClass";
    public static final String SPI_SERVICES_DIR = "META-INF/services";
    public static final String AOT_INPUT_REGISTRY_CLASS = "prunusAotRegistryClass";
    public static final String AOT_INPUT_CLASSES_OUTPUT_DIR = "prunusAotClassesOutputDir";
    public static final String AOT_INPUT_RUNTIME_CLASSPATH = "prunusAotRuntimeClasspath";

    // === Section 6: External contract FQCN / annotation binary names + CallsiteBindingProtocol mirror ===
    /**
     * Contract surface: these FQCN strings mirror real classes in libprunus-core; renaming requires sync with the
     * AotLogSemanticContractTestKit.
     */
    public static final String ABSTRACT_LOG_CONFIG_FQCN = "org.libprunus.core.log.runtime.AbstractLogConfig";

    public static final String SENSITIVE_ANNOTATION_BINARY_NAME = LOG_ANNOTATION_PACKAGE + "Sensitive";
    public static final String DO_NOT_LOG_ANNOTATION_BINARY_NAME = LOG_ANNOTATION_PACKAGE + "DoNotLog";
    public static final String DO_LOG_ANNOTATION_BINARY_NAME = LOG_ANNOTATION_PACKAGE + "DoLog";

    /** Mirror of {@link CallsiteBindingProtocol#RESOURCE_DIR}; kept here so plugin code does not pull libprunus-core runtime classes. */
    public static final String AOT_RUNTIME_CALLSITE_DIR = CallsiteBindingProtocol.RESOURCE_DIR;

    // === Section 7: Generator version ===
    public static final String AOT_GENERATOR_VERSION = "1";

    private PrunusPluginConstants() {
        throw new UnsupportedOperationException();
    }
}
