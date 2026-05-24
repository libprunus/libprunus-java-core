package org.libprunus.core.plugin.aot.log;

import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.pool.TypePool;
import org.libprunus.core.plugin.aot.AotCompileContext;

public final class AotLogByteBuddyPlugin implements Plugin {

    private final AotCompileContext compileContext;
    private final RegistryRouteGraph routeGraph;

    public AotLogByteBuddyPlugin(
            String registryClassName, ClassFileLocator classFileLocator, AotCompileContext compileContext) {
        this.compileContext = compileContext;
        this.routeGraph = new RegistryRouteGraphBuilder()
                .build(registryClassName, classFileLocator, compileContext.sharedTypePool(classFileLocator));
    }

    @Override
    public boolean matches(TypeDescription target) {
        if (target.isInterface() || target.isEnum() || target.isAnnotation()) {
            return false;
        }
        return routeGraph.isRouteRelevant(target);
    }

    @Override
    public DynamicType.Builder<?> apply(
            DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassFileLocator classFileLocator) {
        DynamicType.Builder<?> transformed = builder;
        if (routeGraph.methodEligible(typeDescription)) {
            transformed = transformed.visit(new AotMethodLoggingTransformer(routeGraph));
        }
        if (routeGraph.toStringEligible(typeDescription)) {
            TypePool sharedTypePool = compileContext.sharedTypePool(classFileLocator);
            TypeDescription interfaceType = sharedTypePool
                    .describe(WeavingInternalNames.AOT_LOGGABLE_BINARY_NAME)
                    .resolve();
            transformed =
                    transformed.implement(interfaceType).visit(new AotPojoTransformer(typeDescription, routeGraph));
        }
        return transformed;
    }

    @Override
    public void close() {
        compileContext.clear();
    }
}
