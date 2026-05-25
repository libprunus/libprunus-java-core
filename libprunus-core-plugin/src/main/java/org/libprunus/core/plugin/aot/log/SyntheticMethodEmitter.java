package org.libprunus.core.plugin.aot.log;

import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

final class SyntheticMethodEmitter {

    private SyntheticMethodEmitter() {
        throw new UnsupportedOperationException();
    }

    static void emitEnrichMethod(ClassVisitor cv, List<FieldExtractorRef> fieldExtractors) {
        MethodVisitor mv = cv.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                WeavingInternalNames.SYNTHETIC_ENRICH_METHOD,
                AsmDescriptors.ENRICH_METHOD_DESCRIPTOR,
                null,
                null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        for (FieldExtractorRef extractor : fieldExtractors) {
            Type returnType = Type.getReturnType(extractor.methodDescriptor());
            Type[] argTypes = Type.getArgumentTypes(extractor.methodDescriptor());
            if (returnType == Type.VOID_TYPE || argTypes.length > 0) {
                throw new IllegalStateException("Field extractor must be a no-arg method and cannot return void: "
                        + extractor.ownerInternalName() + "#" + extractor.methodName());
            }
            mv.visitLdcInsn(extractor.fieldName());
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    extractor.ownerInternalName(),
                    extractor.methodName(),
                    extractor.methodDescriptor(),
                    extractor.isInterface());
            emitAutoboxing(mv, returnType);
            mv.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE,
                    AsmDescriptors.LOGGING_EVENT_BUILDER_INTERNAL_NAME,
                    "addKeyValue",
                    AsmDescriptors.ADD_KEY_VALUE_DESCRIPTOR,
                    true);
        }
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    static String buildSyntheticEnterDescriptor(MethodDescription method) {
        List<Type> types = new ArrayList<>();
        types.add(Type.getObjectType(AsmDescriptors.LOGGER_INTERNAL_NAME));
        for (ParameterDescription param : method.getParameters()) {
            types.add(Type.getType(param.getType().asErasure().getDescriptor()));
        }
        return Type.getMethodDescriptor(Type.VOID_TYPE, types.toArray(Type[]::new));
    }

    static String buildSyntheticExitDescriptor(Type returnType) {
        Type loggerType = Type.getObjectType(AsmDescriptors.LOGGER_INTERNAL_NAME);
        if (returnType == Type.VOID_TYPE) {
            return Type.getMethodDescriptor(Type.VOID_TYPE, loggerType);
        }
        return Type.getMethodDescriptor(Type.VOID_TYPE, loggerType, returnType);
    }

    private static void emitAutoboxing(MethodVisitor mv, Type returnType) {
        switch (returnType.getSort()) {
            case Type.BOOLEAN -> emitValueOf(mv, "java/lang/Boolean", "Z");
            case Type.BYTE -> emitValueOf(mv, "java/lang/Byte", "B");
            case Type.CHAR -> emitValueOf(mv, "java/lang/Character", "C");
            case Type.SHORT -> emitValueOf(mv, "java/lang/Short", "S");
            case Type.INT -> emitValueOf(mv, "java/lang/Integer", "I");
            case Type.LONG -> emitValueOf(mv, "java/lang/Long", "J");
            case Type.FLOAT -> emitValueOf(mv, "java/lang/Float", "F");
            case Type.DOUBLE -> emitValueOf(mv, "java/lang/Double", "D");
            default -> {
                /* reference types need no boxing */
            }
        }
    }

    private static void emitValueOf(MethodVisitor mv, String wrapperInternalName, String primDescriptor) {
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                wrapperInternalName,
                "valueOf",
                "(" + primDescriptor + ")L" + wrapperInternalName + ";",
                false);
    }
}
