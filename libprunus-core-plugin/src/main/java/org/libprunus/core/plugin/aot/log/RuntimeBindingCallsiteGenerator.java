package org.libprunus.core.plugin.aot.log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.libprunus.core.plugin.aot.AsmClassFileVersionResolver;
import org.libprunus.core.plugin.aot.BindingIdSanitizer;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;
import org.libprunus.core.plugin.aot.util.AtomicFileWriter;

public final class RuntimeBindingCallsiteGenerator {

    public static String callsiteClassName(String bindingId) {
        String sanitizedId = BindingIdSanitizer.sanitizeForPackageSegment(bindingId);
        return PrunusPluginConstants.GENERATED_AOT_PACKAGE
                + "."
                + sanitizedId
                + "."
                + PrunusPluginConstants.GENERATED_AOT_RUNTIME_CALLSITE_SIMPLE_NAME;
    }

    public void generate(String bindingId, String selectedBindingClass, Path outputDir, String targetCompatibility)
            throws IOException {
        Objects.requireNonNull(selectedBindingClass, "selectedBindingClass");
        Objects.requireNonNull(outputDir, "outputDir");
        String className = callsiteClassName(bindingId);
        byte[] bytes = generateBytes(className, selectedBindingClass, targetCompatibility);
        Path classFile = outputDir.resolve(className.replace('.', '/') + ".class");
        AtomicFileWriter.writeIfChanged(classFile, bytes);
    }

    byte[] generateBytes(String callsiteClassName, String selectedBindingClass, String targetCompatibility) {
        String callsiteInternal = callsiteClassName.replace('.', '/');
        String selectedBindingInternal = selectedBindingClass.replace('.', '/');
        String abstractInternal = PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN.replace('.', '/');
        String logRuntimeInternal = RuntimeBindingAbi.AOT_RUNTIME_INTERNAL_NAME;
        int classFileVersion = AsmClassFileVersionResolver.resolve(targetCompatibility);

        ClassWriter cw = AsmGenSupport.beginPublicFinalClass(classFileVersion, callsiteInternal, "java/lang/Object");

        AsmGenSupport.emitDefaultCtor(cw, "java/lang/Object", Opcodes.ACC_PRIVATE);

        MethodVisitor bind = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bind", "()V", null, null);
        bind.visitCode();
        bind.visitTypeInsn(Opcodes.NEW, selectedBindingInternal);
        bind.visitInsn(Opcodes.DUP);
        bind.visitMethodInsn(Opcodes.INVOKESPECIAL, selectedBindingInternal, "<init>", "()V", false);
        bind.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                logRuntimeInternal,
                RuntimeBindingAbi.INITIALIZE_BINDING_METHOD,
                "(L" + abstractInternal + ";)V",
                false);
        bind.visitInsn(Opcodes.RETURN);
        bind.visitMaxs(0, 0);
        bind.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
