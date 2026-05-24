package org.libprunus.core.plugin.aot.log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription.InDefinedShape;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;
import org.libprunus.core.plugin.aot.JvmDescriptor;

final class AotPojoTransformer extends AsmVisitorWrapper.AbstractBase {

    private final TypeDescription instrumentedType;
    private final RegistryRouteGraph routeGraph;

    AotPojoTransformer(TypeDescription instrumentedType, RegistryRouteGraph routeGraph) {
        this.instrumentedType = instrumentedType;
        this.routeGraph = routeGraph;
    }

    @Override
    public int mergeWriter(int flags) {
        return flags | ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS;
    }

    @Override
    public ClassVisitor wrap(
            TypeDescription typeDescription,
            ClassVisitor classVisitor,
            Implementation.Context implementationContext,
            TypePool typePool,
            FieldList<InDefinedShape> fields,
            MethodList<?> methods,
            int writerFlags,
            int readerFlags) {
        List<FieldRenderSlot> slots = routeGraph.toStringFieldChain(typeDescription);
        return new ClassVisitor(Opcodes.ASM9, classVisitor) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                if (("toString".equals(name) && "()Ljava/lang/String;".equals(descriptor))
                        || (WeavingInternalNames.AOT_RENDER_METHOD.equals(name)
                                && WeavingInternalNames.AOT_RENDER_DESCRIPTOR.equals(descriptor))) {
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visitEnd() {
                writeToStringTrampoline(cv, typeDescription);
                writeRenderMethod(cv, typeDescription, slots);
                super.visitEnd();
            }
        };
    }

    private void writeToStringTrampoline(ClassVisitor visitor, TypeDescription typeDescription) {
        String ownerFqcn = typeDescription.getName() + "#toString";
        MethodVisitor mv = visitor.visitMethod(Opcodes.ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
        mv.visitCode();

        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                WeavingInternalNames.STRING_BUILDER_POOL_INTERNAL_NAME,
                "acquire",
                AsmDescriptors.STRING_BUILDER_ACQUIRE_DESCRIPTOR,
                false);
        mv.visitVarInsn(Opcodes.ASTORE, 1);

        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchHandler = new Label();

        mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Throwable");

        mv.visitLabel(tryStart);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME,
                "getGlobalMaxMessageLength",
                "()I",
                false);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "setMaxMessageLength",
                AsmDescriptors.CONTEXT_SET_MAX_MESSAGE_LENGTH_DESCRIPTOR,
                false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                instrumentedType.getInternalName(),
                WeavingInternalNames.AOT_RENDER_METHOD,
                WeavingInternalNames.AOT_RENDER_DESCRIPTOR,
                false);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "toString",
                AsmDescriptors.STRING_BUILDER_TO_STRING_DESCRIPTOR,
                false);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitLabel(tryEnd);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                WeavingInternalNames.STRING_BUILDER_POOL_INTERNAL_NAME,
                "release",
                AsmDescriptors.STRING_BUILDER_RELEASE_DESCRIPTOR,
                false);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitLabel(catchHandler);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitLdcInsn(ownerFqcn);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "recoverToStringFallback",
                AsmDescriptors.RUNTIME_TO_STRING_FALLBACK_DESCRIPTOR,
                false);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeRenderMethod(ClassVisitor visitor, TypeDescription typeDescription, List<FieldRenderSlot> slots) {
        MethodVisitor mv = visitor.visitMethod(
                Opcodes.ACC_PUBLIC,
                WeavingInternalNames.AOT_RENDER_METHOD,
                WeavingInternalNames.AOT_RENDER_DESCRIPTOR,
                null,
                null);
        mv.visitCode();
        writeRenderMethodBody(mv, typeDescription, slots);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeRenderMethodBody(MethodVisitor mv, TypeDescription typeDescription, List<FieldRenderSlot> slots) {
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "isTruncated",
                AsmDescriptors.CONTEXT_IS_TRUNCATED_DESCRIPTOR,
                false);
        Label continueLabel = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitLabel(continueLabel);

        Set<String> shadowedFieldNames = shadowedFieldNames(slots);
        String renderedClassName = routeGraph.classNameOf(typeDescription);
        StringBuilder fixedAccumulator = new StringBuilder(renderedClassName).append("(");

        Label truncationLabel = new Label();

        for (int index = 0; index < slots.size(); index++) {
            FieldRenderSlot slot = slots.get(index);
            if (index > 0) {
                fixedAccumulator.append(", ");
            }
            switch (slot.family()) {
                case MASK -> emitMaskedField(slot, shadowedFieldNames, fixedAccumulator);
                case PASS_THROUGH -> {
                    emitUnmaskedField(mv, slot, shadowedFieldNames, fixedAccumulator, 1);
                    emitBudgetGuard(mv, 1, truncationLabel);
                }
                case SUPPRESS, NONE ->
                    throw new IllegalStateException("SUPPRESS/NONE field leaked into render chain: "
                            + slot.declaringClassInternalName() + "#" + slot.name());
            }
        }
        fixedAccumulator.append(")");
        AsmEmitHelper.appendString(mv, 1, fixedAccumulator.toString());
        emitBudgetGuard(mv, 1, truncationLabel);

        mv.visitInsn(Opcodes.RETURN);

        mv.visitLabel(truncationLabel);
        AsmEmitHelper.markRenderTruncation(mv, 1);
        mv.visitInsn(Opcodes.RETURN);
    }

    private static void emitMaskedField(
            FieldRenderSlot slot, Set<String> shadowedFieldNames, StringBuilder fixedAccumulator) {
        fixedAccumulator
                .append(fieldLabel(slot, shadowedFieldNames))
                .append("=")
                .append(WeavingInternalNames.MASK_SENTINEL);
    }

    private static void emitUnmaskedField(
            MethodVisitor mv,
            FieldRenderSlot slot,
            Set<String> shadowedFieldNames,
            StringBuilder fixedAccumulator,
            int contextSlot) {
        fixedAccumulator.append(fieldLabel(slot, shadowedFieldNames)).append("=");
        AsmEmitHelper.appendString(mv, contextSlot, fixedAccumulator.toString());
        emitRenderedFieldValue(mv, slot, contextSlot);
        fixedAccumulator.setLength(0);
    }

    private static void emitBudgetGuard(MethodVisitor mv, int contextSlot, Label truncationLabel) {
        AsmEmitHelper.isTruncated(mv, contextSlot);
        mv.visitJumpInsn(Opcodes.IFNE, truncationLabel);
    }

    private static void emitRenderedFieldValue(MethodVisitor mv, FieldRenderSlot slot, int contextSlot) {
        String descriptor = slot.descriptor();
        if (isArrayDescriptor(descriptor)) {
            emitRefAppend(mv, slot, contextSlot);
            return;
        }
        if (isObjectDescriptor(descriptor)) {
            emitRefAppend(mv, slot, contextSlot);
            return;
        }
        emitPrimitiveAppend(mv, slot, contextSlot);
    }

    private static void emitRefAppend(MethodVisitor mv, FieldRenderSlot slot, int contextSlot) {
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        emitFieldValue(mv, slot);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "render",
                AsmDescriptors.CONTEXT_APPEND_OBJECT_DESCRIPTOR,
                false);
    }

    private static void emitPrimitiveAppend(MethodVisitor mv, FieldRenderSlot slot, int contextSlot) {
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        emitFieldValue(mv, slot);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "append",
                AsmDescriptors.contextAppendPrimitiveDescriptor(descriptorType(slot.descriptor())),
                false);
        mv.visitInsn(Opcodes.POP);
    }

    private static void emitFieldValue(MethodVisitor mv, FieldRenderSlot slot) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, slot.declaringClassInternalName(), slot.name(), slot.descriptor());
    }

    private static Type descriptorType(String descriptor) {
        return switch (descriptor) {
            case JvmDescriptor.BOOLEAN -> Type.BOOLEAN_TYPE;
            case JvmDescriptor.CHAR -> Type.CHAR_TYPE;
            case JvmDescriptor.LONG -> Type.LONG_TYPE;
            case JvmDescriptor.FLOAT -> Type.FLOAT_TYPE;
            case JvmDescriptor.DOUBLE -> Type.DOUBLE_TYPE;
            default -> Type.INT_TYPE;
        };
    }

    private static boolean isArrayDescriptor(String descriptor) {
        return descriptor.startsWith("[");
    }

    private static boolean isObjectDescriptor(String descriptor) {
        return descriptor.length() >= 2 && descriptor.charAt(0) == 'L';
    }

    private static Set<String> shadowedFieldNames(List<FieldRenderSlot> slots) {
        Set<String> duplicates = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (FieldRenderSlot slot : slots) {
            if (!seen.add(slot.name())) {
                duplicates.add(slot.name());
            }
        }
        return duplicates;
    }

    private static String fieldLabel(FieldRenderSlot slot, Set<String> shadowedFieldNames) {
        if (!shadowedFieldNames.contains(slot.name())) {
            return slot.name();
        }
        return slot.name() + "(" + slot.declaringClassSimpleName() + ")";
    }
}
