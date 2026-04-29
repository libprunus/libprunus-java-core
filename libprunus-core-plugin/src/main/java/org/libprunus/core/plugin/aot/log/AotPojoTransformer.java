package org.libprunus.core.plugin.aot.log;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.libprunus.core.log.runtime.AotLogRuntime;
import org.libprunus.core.log.runtime.AotLoggable;
import org.libprunus.core.plugin.aot.AotCompileContext;
import org.libprunus.core.plugin.aot.AotCompileContext.AotClassMetadata;
import org.libprunus.core.plugin.aot.AotCompileContext.AotFieldMetadata;

final class AotPojoTransformer extends AsmVisitorWrapper.AbstractBase {

    private static final int MAX_ARRAY_LOG_ELEMENTS = 100;
    private static final String AOT_RENDER_METHOD = "_libprunus_render";
    private static final String AOT_RENDER_DESCRIPTOR = "(Ljava/lang/StringBuilder;I)V";
    private static final String LEGACY_AOT_TOSTRING_METHOD = "_libprunus_toString";
    private static final String LEGACY_AOT_TOSTRING_DESCRIPTOR = "(I)Ljava/lang/String;";
    private static final String BUILDER_INTERNAL_NAME = "java/lang/StringBuilder";

    private final TypeDescription instrumentedType;
    private final TypePoolAdapter typePoolAdapter;
    private final int maxToStringDepth;
    private final AotCompileContext compileContext;
    private final boolean handleInaccessibleField;
    private final String instrumentedPackage;

    AotPojoTransformer(
            TypeDescription instrumentedType,
            TypePool sharedTypePool,
            int maxToStringDepth,
            AotCompileContext compileContext,
            boolean handleInaccessibleField) {
        this.instrumentedType = instrumentedType;
        this.typePoolAdapter = new TypePoolAdapter(sharedTypePool);
        this.maxToStringDepth = maxToStringDepth;
        this.compileContext = compileContext;
        this.handleInaccessibleField = handleInaccessibleField;
        this.instrumentedPackage = packageName(instrumentedType.getInternalName());
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
        AotClassMetadata metadata = compileContext.resolveMetadata(
                typeDescription.getName(),
                className -> className.equals(typeDescription.getName())
                        ? typeDescription
                        : typePoolAdapter.resolve(className));
        List<AotFieldMetadata> fieldMetadata = flattenFieldsForPrinting(metadata);
        return new ClassVisitor(Opcodes.ASM9, classVisitor) {
            @Override
            public void visit(
                    int version, int access, String name, String signature, String superName, String[] interfaces) {
                String[] updatedInterfaces = appendInterface(interfaces, Type.getInternalName(AotLoggable.class));
                super.visit(version, access, name, signature, superName, updatedInterfaces);
            }

            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                if (("toString".equals(name) && "()Ljava/lang/String;".equals(descriptor))
                        || (AOT_RENDER_METHOD.equals(name) && AOT_RENDER_DESCRIPTOR.equals(descriptor))
                        || (LEGACY_AOT_TOSTRING_METHOD.equals(name)
                                && LEGACY_AOT_TOSTRING_DESCRIPTOR.equals(descriptor))) {
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visitEnd() {
                writeToStringTrampoline(cv, fieldMetadata);
                writeRenderMethod(cv, typeDescription, fieldMetadata);
                super.visitEnd();
            }
        };
    }

    private List<AotFieldMetadata> flattenFieldsForPrinting(AotClassMetadata rootMetadata) {
        if (rootMetadata == null) {
            return List.of();
        }

        Map<String, AotFieldMetadata> fieldByOwnerAndName = new LinkedHashMap<>();
        AotClassMetadata current = rootMetadata;
        boolean rootClass = true;
        while (current != null) {
            String currentPackage = packageName(current.internalName());
            for (AotFieldMetadata field : current.declaredFields()) {
                collectField(fieldByOwnerAndName, field, current.internalName(), currentPackage, rootClass);
            }
            current = current.parent();
            rootClass = false;
        }
        return new ArrayList<>(fieldByOwnerAndName.values());
    }

    private void collectField(
            Map<String, AotFieldMetadata> fieldByOwnerAndName,
            AotFieldMetadata field,
            String ownerInternalName,
            String ownerPackage,
            boolean rootClass) {
        String fieldKey = fieldKey(field);
        if (shouldIgnoreField(fieldByOwnerAndName, field, fieldKey, ownerPackage, rootClass)) {
            return;
        }
        AotFieldMetadata collected = collectedField(field, ownerInternalName, rootClass);
        if (collected != null) {
            fieldByOwnerAndName.put(fieldKey, collected);
        }
    }

    private boolean shouldIgnoreField(
            Map<String, AotFieldMetadata> fieldByOwnerAndName,
            AotFieldMetadata field,
            String fieldKey,
            String ownerPackage,
            boolean rootClass) {
        if (field.isStatic() || fieldByOwnerAndName.containsKey(fieldKey)) {
            return true;
        }
        if (rootClass) {
            return false;
        }
        return field.isPackagePrivate() && !samePackage(instrumentedPackage, ownerPackage);
    }

    private AotFieldMetadata collectedField(AotFieldMetadata field, String ownerInternalName, boolean rootClass) {
        if (requiresSpecialAccessHandling(field, rootClass)) {
            if (!handleInaccessibleField && field.inaccessibleByPolicy()) {
                return null;
            }
            return handledInaccessibleField(field, ownerInternalName);
        }
        return field;
    }

    private boolean requiresSpecialAccessHandling(AotFieldMetadata field, boolean rootClass) {
        return !rootClass && field.isPrivate();
    }

    private AotFieldMetadata handledInaccessibleField(AotFieldMetadata field, String ownerInternalName) {
        return canUseAccessor(field, ownerInternalName) ? field.asAccessorBridge() : field.asInaccessiblePlaceholder();
    }

    private static String fieldKey(AotFieldMetadata field) {
        return field.ownerInternalName() + "#" + field.name();
    }

    private void writeToStringTrampoline(ClassVisitor visitor, List<AotFieldMetadata> fields) {
        MethodVisitor mv = visitor.visitMethod(Opcodes.ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        int computedCapacity = computeBuilderCapacity(fields.size());
        mv.visitTypeInsn(Opcodes.NEW, BUILDER_INTERNAL_NAME);
        mv.visitInsn(Opcodes.DUP);
        pushInt(mv, computedCapacity);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, BUILDER_INTERNAL_NAME, "<init>", "(I)V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                instrumentedType.getInternalName(),
                AOT_RENDER_METHOD,
                AOT_RENDER_DESCRIPTOR,
                false);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BUILDER_INTERNAL_NAME, "toString", "()Ljava/lang/String;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeRenderMethod(
            ClassVisitor visitor, TypeDescription typeDescription, List<AotFieldMetadata> fields) {
        MethodVisitor mv =
                visitor.visitMethod(Opcodes.ACC_PUBLIC, AOT_RENDER_METHOD, AOT_RENDER_DESCRIPTOR, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitLdcInsn(maxToStringDepth);
        Label continueLabel = new Label();
        mv.visitJumpInsn(Opcodes.IF_ICMPLT, continueLabel);
        emitAppendString(mv, 1, "[DEPTH_LIMIT]");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitLabel(continueLabel);

        Set<String> shadowedFieldNames = shadowedFieldNames(fields);
        emitAppendString(mv, 1, typeDescription.getSimpleName());
        emitAppendChar(mv, 1, '(');
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                emitAppendString(mv, 1, ", ");
            }
            emitAppendString(mv, 1, fieldLabel(fields.get(index), shadowedFieldNames) + "=");
            emitRenderedFieldValue(mv, fields.get(index), 1, 2, maxToStringDepth);
        }
        emitAppendChar(mv, 1, ')');
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitAppendString(MethodVisitor mv, int builderSlot, String value) {
        mv.visitVarInsn(Opcodes.ALOAD, builderSlot);
        mv.visitLdcInsn(value);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                BUILDER_INTERNAL_NAME,
                "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mv.visitInsn(Opcodes.POP);
    }

    private static void emitAppendChar(MethodVisitor mv, int builderSlot, char value) {
        mv.visitVarInsn(Opcodes.ALOAD, builderSlot);
        mv.visitIntInsn(Opcodes.SIPUSH, value);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL, BUILDER_INTERNAL_NAME, "append", "(C)Ljava/lang/StringBuilder;", false);
        mv.visitInsn(Opcodes.POP);
    }

    private void emitRenderedFieldValue(
            MethodVisitor mv, AotFieldMetadata field, int builderSlot, int currentDepthSlot, int maxToStringDepth) {
        if (field.masked()) {
            emitAppendString(mv, builderSlot, "***");
            return;
        }
        if (field.inaccessibleByPolicy()) {
            emitAppendString(mv, builderSlot, "[INACCESSIBLE]");
            return;
        }
        String descriptor = field.renderingDescriptor();
        if (isArrayDescriptor(descriptor)) {
            emitArrayAppend(mv, field, builderSlot, currentDepthSlot, maxToStringDepth);
            return;
        }
        if (isObjectDescriptor(descriptor)) {
            emitObjectAppend(mv, field, builderSlot, currentDepthSlot, maxToStringDepth);
            return;
        }
        emitPrimitiveAppend(mv, field, builderSlot);
    }

    private void emitArrayAppend(
            MethodVisitor mv, AotFieldMetadata field, int builderSlot, int currentDepthSlot, int maxToStringDepth) {
        mv.visitVarInsn(Opcodes.ALOAD, builderSlot);
        emitFieldValue(mv, field);
        mv.visitVarInsn(Opcodes.ILOAD, currentDepthSlot);
        mv.visitLdcInsn(MAX_ARRAY_LOG_ELEMENTS);
        mv.visitLdcInsn(maxToStringDepth);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(AotLogRuntime.class),
                "appendArrayTo",
                "(Ljava/lang/StringBuilder;Ljava/lang/Object;III)V",
                false);
    }

    private void emitObjectAppend(
            MethodVisitor mv, AotFieldMetadata field, int builderSlot, int currentDepthSlot, int maxToStringDepth) {
        mv.visitVarInsn(Opcodes.ALOAD, builderSlot);
        emitFieldValue(mv, field);
        mv.visitVarInsn(Opcodes.ILOAD, currentDepthSlot);
        mv.visitLdcInsn(MAX_ARRAY_LOG_ELEMENTS);
        mv.visitLdcInsn(maxToStringDepth);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(AotLogRuntime.class),
                "appendObjectTo",
                "(Ljava/lang/StringBuilder;Ljava/lang/Object;III)V",
                false);
    }

    private void emitPrimitiveAppend(MethodVisitor mv, AotFieldMetadata field, int builderSlot) {
        mv.visitVarInsn(Opcodes.ALOAD, builderSlot);
        emitFieldValue(mv, field);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                BUILDER_INTERNAL_NAME,
                "append",
                appendDescriptor(field.renderingDescriptor()),
                false);
        mv.visitInsn(Opcodes.POP);
    }

    private void emitFieldValue(MethodVisitor mv, AotFieldMetadata field) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        if (field.renderThroughAccessor() && canReadFieldDirectly(field)) {
            mv.visitFieldInsn(Opcodes.GETFIELD, field.ownerInternalName(), field.name(), field.descriptor());
            return;
        }
        if (field.renderThroughAccessor()) {
            int invokeOpcode = accessorInvokeOpcode(field.accessorAccessFlags());
            if (invokeOpcode == Opcodes.INVOKESTATIC) {
                if (!field.accessorDescriptor().startsWith("()")) {
                    throw new IllegalStateException(
                            "AOT Log Plugin: Unsupported static accessor with parameters detected for field "
                                    + field.name());
                }
                mv.visitInsn(Opcodes.POP);
            }
            mv.visitMethodInsn(
                    invokeOpcode,
                    field.accessorOwnerInternalName(),
                    field.accessorName(),
                    field.accessorDescriptor(),
                    false);
            return;
        }
        mv.visitFieldInsn(Opcodes.GETFIELD, field.ownerInternalName(), field.name(), field.descriptor());
    }

    private boolean canReadFieldDirectly(AotFieldMetadata field) {
        if (field.ownerInternalName().equals(instrumentedType.getInternalName())) {
            return true;
        }
        if (field.isPublic() || field.isProtected()) {
            return true;
        }
        return field.isPackagePrivate() && samePackage(instrumentedPackage, packageName(field.ownerInternalName()));
    }

    private static int accessorInvokeOpcode(int accessorAccessFlags) {
        if (Modifier.isStatic(accessorAccessFlags)) {
            return Opcodes.INVOKESTATIC;
        }
        if (Modifier.isPrivate(accessorAccessFlags)) {
            return Opcodes.INVOKESPECIAL;
        }
        return Opcodes.INVOKEVIRTUAL;
    }

    private static String appendDescriptor(String descriptor) {
        return switch (descriptor) {
            case "Z" -> "(Z)Ljava/lang/StringBuilder;";
            case "C" -> "(C)Ljava/lang/StringBuilder;";
            case "J" -> "(J)Ljava/lang/StringBuilder;";
            case "F" -> "(F)Ljava/lang/StringBuilder;";
            case "D" -> "(D)Ljava/lang/StringBuilder;";
            default -> "(I)Ljava/lang/StringBuilder;";
        };
    }

    private static String[] appendInterface(String[] interfaces, String interfaceInternalName) {
        if (interfaces != null) {
            for (String existing : interfaces) {
                if (existing.equals(interfaceInternalName)) {
                    return interfaces;
                }
            }
        }
        if (interfaces == null || interfaces.length == 0) {
            return new String[] {interfaceInternalName};
        }
        String[] updated = new String[interfaces.length + 1];
        System.arraycopy(interfaces, 0, updated, 0, interfaces.length);
        updated[interfaces.length] = interfaceInternalName;
        return updated;
    }

    private static boolean isArrayDescriptor(String descriptor) {
        return descriptor.startsWith("[");
    }

    private static boolean isObjectDescriptor(String descriptor) {
        return descriptor.length() >= 2 && descriptor.charAt(0) == 'L';
    }

    private static Set<String> shadowedFieldNames(List<AotFieldMetadata> fields) {
        Set<String> duplicates = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (AotFieldMetadata field : fields) {
            if (!seen.add(field.name())) {
                duplicates.add(field.name());
            }
        }
        return duplicates;
    }

    private static String fieldLabel(AotFieldMetadata field, Set<String> shadowedFieldNames) {
        if (!shadowedFieldNames.contains(field.name())) {
            return field.name();
        }
        return field.name() + "(" + ownerSimpleName(field.ownerInternalName()) + ")";
    }

    private static String ownerSimpleName(String ownerInternalName) {
        int slashIndex = ownerInternalName.lastIndexOf('/');
        if (slashIndex < 0) {
            return ownerInternalName;
        }
        return ownerInternalName.substring(slashIndex + 1);
    }

    private static final class TypePoolAdapter {

        private final TypePool typePool;

        private TypePoolAdapter(TypePool typePool) {
            this.typePool = typePool;
        }

        private TypeDescription resolve(String className) {
            return typePool.describe(className).resolve();
        }
    }

    private static String packageName(String internalName) {
        int slashIndex = internalName.lastIndexOf('/');
        if (slashIndex < 0) {
            return "";
        }
        return internalName.substring(0, slashIndex);
    }

    private static boolean samePackage(String leftPackage, String rightPackage) {
        return leftPackage.equals(rightPackage);
    }

    private static int computeBuilderCapacity(int fieldCount) {
        return Math.max(16, fieldCount * 32);
    }

    private static void pushInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(value == -1 ? Opcodes.ICONST_M1 : Opcodes.ICONST_0 + value);
            return;
        }
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
            return;
        }
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
            return;
        }
        mv.visitLdcInsn(value);
    }

    private boolean canUseAccessor(AotFieldMetadata field, String ownerInternalName) {
        if (!field.hasAccessor()) {
            return false;
        }
        boolean declaredInCurrentClass = field.ownerInternalName().equals(instrumentedType.getInternalName());
        if (!declaredInCurrentClass && field.accessorIsPrivate()) {
            return false;
        }
        if (field.accessorIsPublic() || field.accessorIsProtected()) {
            return true;
        }
        return field.accessorIsPackagePrivate() && samePackage(instrumentedPackage, packageName(ownerInternalName));
    }
}
