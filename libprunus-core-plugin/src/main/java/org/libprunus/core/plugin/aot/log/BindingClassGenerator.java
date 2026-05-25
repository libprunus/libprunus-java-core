package org.libprunus.core.plugin.aot.log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.libprunus.core.plugin.aot.AsmClassFileVersionResolver;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;
import org.libprunus.core.plugin.aot.util.AtomicFileWriter;

public final class BindingClassGenerator {

    private static final String WHITELIST_FIELD_NAME = "$WHITELIST";
    private static final String WHITELIST_FIELD_DESCRIPTOR = "[Ljava/lang/String;";
    private static final String WHITELIST_CACHE_FIELD_NAME = "$WHITELIST_CACHE";
    private static final String WHITELIST_CACHE_FIELD_DESCRIPTOR = "Ljava/lang/ClassValue;";
    static final int MAX_WHITELIST_NAMES = 8000;

    public void generate(
            String bindingClassName,
            Path outputDir,
            String targetCompatibility,
            int maxMessageLength,
            List<String> whitelistNames)
            throws IOException {
        Objects.requireNonNull(bindingClassName, "bindingClassName");
        Objects.requireNonNull(outputDir, "outputDir");
        byte[] bytes = generateBytes(bindingClassName, targetCompatibility, maxMessageLength, whitelistNames);
        Path classFile = outputDir.resolve(bindingClassName.replace('.', '/') + ".class");
        AtomicFileWriter.writeIfChanged(classFile, bytes);
    }

    byte[] generateBytes(
            String bindingClassName, String targetCompatibility, int maxMessageLength, List<String> whitelistNames) {
        if (whitelistNames.size() > MAX_WHITELIST_NAMES) {
            throw new IllegalStateException("Whitelist size "
                    + whitelistNames.size()
                    + " exceeds the maximum of "
                    + MAX_WHITELIST_NAMES
                    + " entries. Each entry consumes ~8 bytes of <clinit> bytecode;"
                    + " the JVM hard limit is 65535 bytes.");
        }
        String bindingInternal = bindingClassName.replace('.', '/');
        String abstractInternal = PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN.replace('.', '/');
        String logRuntimeInternal = RuntimeBindingAbi.AOT_RUNTIME_INTERNAL_NAME;
        int classFileVersion = AsmClassFileVersionResolver.resolve(targetCompatibility);

        ClassWriter cw = AsmGenSupport.beginPublicFinalClass(classFileVersion, bindingInternal, abstractInternal);

        FieldVisitor whitelistField = cw.visitField(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                WHITELIST_FIELD_NAME,
                WHITELIST_FIELD_DESCRIPTOR,
                null,
                null);
        whitelistField.visitEnd();

        FieldVisitor whitelistCacheField = cw.visitField(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                WHITELIST_CACHE_FIELD_NAME,
                WHITELIST_CACHE_FIELD_DESCRIPTOR,
                null,
                null);
        whitelistCacheField.visitEnd();

        emitClinit(cw, bindingInternal, logRuntimeInternal, whitelistNames);

        AsmGenSupport.emitDefaultCtor(cw, abstractInternal, Opcodes.ACC_PUBLIC);

        MethodVisitor maxLenMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "getMaxMessageLength", "()I", null, null);
        maxLenMethod.visitCode();
        AsmGenSupport.pushInt(maxLenMethod, maxMessageLength);
        maxLenMethod.visitInsn(Opcodes.IRETURN);
        maxLenMethod.visitMaxs(0, 0);
        maxLenMethod.visitEnd();

        MethodVisitor whiteListMethod = cw.visitMethod(
                Opcodes.ACC_PUBLIC, "isWhitelisted", "(Ljava/lang/Class;)Z", "(Ljava/lang/Class<*>;)Z", null);
        whiteListMethod.visitCode();
        whiteListMethod.visitVarInsn(Opcodes.ALOAD, 1);
        whiteListMethod.visitFieldInsn(
                Opcodes.GETSTATIC, bindingInternal, WHITELIST_CACHE_FIELD_NAME, WHITELIST_CACHE_FIELD_DESCRIPTOR);
        whiteListMethod.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                logRuntimeInternal,
                RuntimeBindingAbi.RUNTIME_IS_WHITELISTED_CACHED_METHOD,
                RuntimeBindingAbi.RUNTIME_IS_WHITELISTED_CACHED_DESCRIPTOR,
                false);
        whiteListMethod.visitInsn(Opcodes.IRETURN);
        whiteListMethod.visitMaxs(0, 0);
        whiteListMethod.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void emitClinit(
            ClassWriter cw, String bindingInternal, String logRuntimeInternal, List<String> whitelistNames) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        AsmGenSupport.pushInt(mv, whitelistNames.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
        for (int i = 0; i < whitelistNames.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            AsmGenSupport.pushInt(mv, i);
            mv.visitLdcInsn(whitelistNames.get(i));
            mv.visitInsn(Opcodes.AASTORE);
        }
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, bindingInternal, WHITELIST_FIELD_NAME, WHITELIST_FIELD_DESCRIPTOR);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                logRuntimeInternal,
                RuntimeBindingAbi.RUNTIME_BUILD_WHITELIST_CACHE_METHOD,
                RuntimeBindingAbi.RUNTIME_BUILD_WHITELIST_CACHE_DESCRIPTOR,
                false);
        mv.visitFieldInsn(
                Opcodes.PUTSTATIC, bindingInternal, WHITELIST_CACHE_FIELD_NAME, WHITELIST_CACHE_FIELD_DESCRIPTOR);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
