package org.libprunus.core.plugin.aot.log;

import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.pool.TypePool;
import org.libprunus.core.log.annotation.AotNoReplace;
import org.libprunus.core.log.annotation.Sensitive;
import org.libprunus.core.plugin.aot.AotCompileContext;
import org.libprunus.core.plugin.aot.AotPluginArguments;
import org.libprunus.core.plugin.aot.PackagePrefixMatcher;

public final class AotLogByteBuddyPlugin implements Plugin {

    private final boolean enabled;
    private final PackagePrefixMatcher baseMatcher;
    private final PackagePrefixMatcher excludeMatcher;
    private final String[] targetClassSuffixes;
    private final String[] pojoSuffixes;
    private final String classNameFormat;
    private final AotLogLevel enterLogLevel;
    private final AotLogLevel exitLogLevel;
    private final boolean handleInaccessibleField;
    private final int maxToStringDepth;
    private final AotCompileContext compileContext;

    public AotLogByteBuddyPlugin(AotPluginArguments arguments) {
        this(arguments, new AotCompileContext(arguments.log().handleInaccessibleField(), arguments.basePackages()));
    }

    public AotLogByteBuddyPlugin(AotPluginArguments arguments, AotCompileContext compileContext) {
        this.enabled = arguments.log().enabled();
        this.baseMatcher = new PackagePrefixMatcher(arguments.basePackages(), true);
        this.excludeMatcher = new PackagePrefixMatcher(arguments.excludePackages(), false);
        this.targetClassSuffixes = arguments.log().targetClassSuffixes().toArray(new String[0]);
        this.pojoSuffixes = arguments.log().pojoSuffixes().toArray(new String[0]);
        this.classNameFormat = arguments.log().classNameFormat();
        this.enterLogLevel = AotLogLevel.valueOf(arguments.log().enterLogLevel());
        this.exitLogLevel = AotLogLevel.valueOf(arguments.log().exitLogLevel());
        this.handleInaccessibleField = arguments.log().handleInaccessibleField();
        this.maxToStringDepth = arguments.log().maxToStringDepth();
        this.compileContext = compileContext;
    }

    @Override
    public boolean matches(TypeDescription target) {
        if (!enabled || target.isInterface() || target.isEnum() || target.isAnnotation()) {
            return false;
        }
        if (target.getDeclaredAnnotations().isAnnotationPresent(AotNoReplace.class)) {
            return false;
        }
        String className = target.getName();
        if (!isInScope(className)) {
            return false;
        }
        return shouldApplyMethodAdvice(target) || shouldRewriteToString(target);
    }

    @Override
    public DynamicType.Builder<?> apply(
            DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassFileLocator classFileLocator) {
        DynamicType.Builder<?> transformed = builder;
        if (shouldApplyMethodAdvice(typeDescription)) {
            transformed =
                    transformed.visit(new AotMethodLoggingTransformer(classNameFormat, enterLogLevel, exitLogLevel));
        }
        if (shouldRewriteToString(typeDescription)) {
            TypePool sharedTypePool = compileContext.sharedTypePool(classFileLocator);
            transformed = transformed.visit(new AotPojoTransformer(
                    typeDescription, sharedTypePool, maxToStringDepth, compileContext, handleInaccessibleField));
        }
        return transformed;
    }

    @Override
    public void close() {
        compileContext.clear();
    }

    boolean shouldApplyMethodAdvice(TypeDescription typeDescription) {
        return hasSuffix(typeDescription.getSimpleName(), targetClassSuffixes);
    }

    boolean shouldRewriteToString(TypeDescription typeDescription) {
        return hasSuffix(typeDescription.getSimpleName(), pojoSuffixes)
                || typeDescription.getDeclaredAnnotations().isAnnotationPresent(Sensitive.class);
    }

    private boolean isInScope(String className) {
        return baseMatcher.matches(className) && !excludeMatcher.matches(className);
    }

    private boolean hasSuffix(String simpleName, String[] suffixes) {
        for (String suffix : suffixes) {
            if (compileContext.hasSuffix(simpleName, suffix)) {
                return true;
            }
        }
        return false;
    }
}
