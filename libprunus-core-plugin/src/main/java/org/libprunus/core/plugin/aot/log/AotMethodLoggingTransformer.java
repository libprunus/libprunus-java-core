package org.libprunus.core.plugin.aot.log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription.InDefinedShape;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;
import org.jspecify.annotations.Nullable;
import org.libprunus.core.log.runtime.LogLevel;

final class AotMethodLoggingTransformer extends AsmVisitorWrapper.AbstractBase {

    private final RegistryRouteGraph routeGraph;

    AotMethodLoggingTransformer(RegistryRouteGraph routeGraph) {
        this.routeGraph = routeGraph;
    }

    @Override
    public int mergeWriter(int flags) {
        return flags | ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS;
    }

    @Override
    public ClassVisitor wrap(
            TypeDescription instrumentedType,
            ClassVisitor classVisitor,
            Implementation.Context implementationContext,
            TypePool typePool,
            FieldList<InDefinedShape> fields,
            MethodList<?> methods,
            int writerFlags,
            int readerFlags) {
        MethodLoggingRule rule = routeGraph.methodRuleFor(instrumentedType);
        Set<String> overloadedNames = MethodOverloadResolver.detectOverloadedNames(routeGraph, instrumentedType);
        return new AotMethodLoggingClassVisitor(
                classVisitor, routeGraph, instrumentedType, rule, overloadedNames, methods);
    }

    static String sanitizeForRecipe(String text) {
        int firstBad = -1;
        for (int index = 0; index < text.length(); index++) {
            if (Character.isISOControl(text.charAt(index))) {
                firstBad = index;
                break;
            }
        }
        if (firstBad == -1) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        builder.append(text, 0, firstBad);
        for (int index = firstBad; index < text.length(); index++) {
            char value = text.charAt(index);
            builder.append(Character.isISOControl(value) ? '?' : value);
        }
        return builder.toString();
    }

    record SyntheticMethodRequest(
            MethodDescription method,
            ClassPlanAssembler.MethodPlan methodPlan,
            MethodLogContext context,
            String overloadSuffix) {}

    record MethodLogContext(
            String renderedClassName, String renderedMethodName, LogLevel enterLogLevel, LogLevel exitLogLevel) {}

    private static final class AotMethodLoggingClassVisitor extends ClassVisitor {
        private final RegistryRouteGraph routeGraph;
        private final TypeDescription instrumentedType;
        private final LogLevel enterLogLevel;
        private final LogLevel exitLogLevel;
        private final List<FieldExtractorRef> fieldExtractors;
        private final Set<String> overloadedNames;
        private final Map<String, MethodDescription> methodLookup;
        private final List<SyntheticMethodRequest> syntheticRequests = new ArrayList<>();

        @SuppressWarnings("NullAway") // assigned in ASM visit() before any visitMethod/visitEnd use
        private String classInternalName;

        private AotMethodLoggingClassVisitor(
                ClassVisitor delegate,
                RegistryRouteGraph routeGraph,
                TypeDescription instrumentedType,
                MethodLoggingRule rule,
                Set<String> overloadedNames,
                MethodList<?> methods) {
            super(Opcodes.ASM9, delegate);
            this.routeGraph = routeGraph;
            this.instrumentedType = instrumentedType;
            this.enterLogLevel = rule.entryLevel();
            this.exitLogLevel = rule.exitLevel();
            this.fieldExtractors = rule.fieldExtractors();
            this.overloadedNames = overloadedNames;
            this.methodLookup = new HashMap<>();
            for (MethodDescription md : methods) {
                methodLookup.put(md.getInternalName() + md.getDescriptor(), md);
            }
        }

        @Override
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.classInternalName = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public @Nullable MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            if (WeavingInternalNames.isSyntheticMethodName(name)) {
                return null;
            }
            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
            MethodDescription method = methodLookup.get(name + descriptor);
            if (method == null) {
                return delegate;
            }
            MethodNode methodNode = routeGraph.findDeclaredMethodNode(instrumentedType, name, descriptor);
            if (methodNode == null || !routeGraph.shouldEmitEnterExitFor(methodNode)) {
                return delegate;
            }
            MethodLogContext context = new MethodLogContext(
                    sanitizeForRecipe(routeGraph.declaringClassSimpleNameFor(method)),
                    sanitizeForRecipe(method.getName()),
                    enterLogLevel,
                    exitLogLevel);
            String overloadSuffix = overloadedNames.contains(method.getInternalName())
                    ? MethodOverloadResolver.buildOverloadSuffix(method)
                    : "";
            ClassPlanAssembler.MethodPlan methodPlan =
                    ClassPlanAssembler.assembleFromRouteGraph(method, methodNode, enterLogLevel, exitLogLevel);
            syntheticRequests.add(new SyntheticMethodRequest(method, methodPlan, context, overloadSuffix));
            return new LightweightInjectionVisitor(
                    delegate,
                    method,
                    classInternalName,
                    enterLogLevel,
                    exitLogLevel,
                    overloadSuffix,
                    methodPlan.returnIgnored());
        }

        @Override
        public void visitEnd() {
            if (!fieldExtractors.isEmpty()) {
                SyntheticMethodEmitter.emitEnrichMethod(cv, fieldExtractors);
            }
            for (SyntheticMethodRequest request : syntheticRequests) {
                if (request.context().enterLogLevel() != LogLevel.OFF) {
                    SyntheticEnterEmitter.emit(cv, classInternalName, request, fieldExtractors);
                }
                if (request.context().exitLogLevel() != LogLevel.OFF) {
                    SyntheticExitEmitter.emit(cv, classInternalName, request, fieldExtractors);
                }
            }
            super.visitEnd();
        }
    }
}
