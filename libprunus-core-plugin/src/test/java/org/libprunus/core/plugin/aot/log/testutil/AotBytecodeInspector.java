package org.libprunus.core.plugin.aot.log.testutil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import org.benf.cfr.reader.Main;
import org.libprunus.core.config.CoreRuntimeConfig;
import org.libprunus.core.log.runtime.AbstractLogConfig;
import org.libprunus.core.log.runtime.LogRuntime;
import org.libprunus.core.log.runtime.LogRuntimeConfig;
import org.libprunus.core.plugin.aot.AotCompileContext;
import org.libprunus.core.plugin.aot.log.AotLogByteBuddyPlugin;

/**
 * Standalone inspector that transforms fixture classes through the AOT pipeline and writes the
 * resulting .class files plus javap disassembly and cfr-decompiled sources to disk for human review.
 *
 * <p>Run via the Gradle task {@code inspectAotBytecode}. Output lands in {@code build/aot-inspection/}.
 */
public final class AotBytecodeInspector {

    private static final String FIXTURE_PKG = "org.libprunus.core.plugin.aot.log.fixture.inspect.";
    private static final String REGISTRY_NAME = FIXTURE_PKG + "InspectRegistry";

    private record Case(String simpleName, String label) {}

    private static final List<Case> CASES = List.of(
            new Case("InspectMethodService", "InspectMethodService_MethodAdvice"),
            new Case("InspectSimpleDto", "InspectSimpleDto_PojoToString"),
            new Case("InspectMaskedService", "InspectMaskedService_IgnoreAndMethodMask"),
            new Case("InspectPortAdapter", "InspectPortAdapter_InheritedAnnotationAndFieldExtractor"),
            new Case("InspectMaskedChildDto", "InspectMaskedChildDto_InheritedClassMaskStrategy"),
            new Case("InspectMultiReturnService", "InspectMultiReturnService_LvtShiftAllPrimitives"),
            new Case("InspectChainLeafDto", "InspectChainLeafDto_ThreeLevelInheritanceFieldExtractor"),
            new Case("InspectMixedMaskDto", "InspectMixedMaskDto_ClassSensitiveWithFieldDoLogOverride"),
            new Case("InspectOverriddenMaskChildDto", "InspectOverriddenMaskChildDto_DoLogOverridesParentSensitive"),
            new Case("InspectOverloadService", "InspectOverloadService_OverloadSuffixDisambiguation"),
            new Case("InspectThrowingService", "InspectThrowingService_ExceptionPathExit"),
            new Case("InspectRedundantService", "InspectRedundantService_DiamondInterfaceAnnotationResolution"),
            new Case("InspectGenericService", "InspectGenericService_GenericErasureBridge"),
            new Case("InspectBoundedGenericService", "InspectBoundedGenericService_BoundedGenericErasureBridge"),
            new Case("InspectClassAnnotatedService", "InspectClassAnnotatedService_ClassLevelSensitiveMasksAllParams"),
            new Case("InspectFilterService", "InspectFilterService_VisibilityAndAutomatedProcessingIgnoreFilter"),
            new Case(
                    "InspectIgnoredClassService",
                    "InspectIgnoredClassService_ClassLevelAutomatedProcessingIgnoreSkipsTransform"));

    public static void main(String[] args) throws Exception {
        Path outDir = Path.of(args.length > 0 ? args[0] : "build/aot-inspection");

        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override
            public int getMaxMessageLength() {
                return 512;
            }

            @Override
            public boolean isWhitelisted(Class<?> type) {
                return false;
            }
        });
        LogRuntime.linkToDataPlane(new AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(true))));

        ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(AotBytecodeInspector.class.getClassLoader());
        TypePool pool = TypePool.Default.of(locator);

        Path classesDir = outDir.resolve("classes");
        Path javapDir = outDir.resolve("javap");
        Path decompiledDir = outDir.resolve("decompiled");
        Files.createDirectories(classesDir);
        Files.createDirectories(javapDir);
        Files.createDirectories(decompiledDir);

        for (Case c : CASES) {
            TypeDescription typeDesc =
                    pool.describe(FIXTURE_PKG + c.simpleName()).resolve();
            byte[] bytes;
            try (AotLogByteBuddyPlugin plugin =
                    new AotLogByteBuddyPlugin(REGISTRY_NAME, locator, new AotCompileContext())) {
                bytes = plugin.apply(new ByteBuddy().redefine(typeDesc, locator), typeDesc, locator)
                        .make()
                        .getBytes();
            }

            Path classFile = classesDir.resolve(c.label() + ".class");
            Files.write(classFile, bytes);
            runJavap(classFile, javapDir.resolve(c.label() + ".txt"));
            runCfr(classFile, decompiledDir);
        }

        System.out.println("classes    -> " + classesDir);
        System.out.println("javap      -> " + javapDir);
        System.out.println("decompiled -> " + decompiledDir);
    }

    private static void runJavap(Path classFile, Path outFile) throws Exception {
        String javapBin = ProcessHandle.current()
                .info()
                .command()
                .map(cmd -> Path.of(cmd).getParent().resolve("javap").toString())
                .orElse("javap");
        ProcessBuilder pb = new ProcessBuilder(
                javapBin, "-c", "-p", "-verbose", classFile.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        byte[] output = proc.getInputStream().readAllBytes();
        proc.waitFor();
        Files.write(outFile, output);
    }

    private static void runCfr(Path classFile, Path outputDir) {
        Main.main(new String[] {
            classFile.toAbsolutePath().toString(),
            "--outputdir",
            outputDir.toAbsolutePath().toString(),
            "--caseinsensitivefs",
            "true"
        });
    }

    private AotBytecodeInspector() {
        throw new UnsupportedOperationException();
    }
}
