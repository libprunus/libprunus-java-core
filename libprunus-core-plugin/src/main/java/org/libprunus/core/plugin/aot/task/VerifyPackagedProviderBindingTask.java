package org.libprunus.core.plugin.aot.task;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;
import org.libprunus.core.plugin.aot.log.RuntimeBindingCallsiteGenerator;
import org.libprunus.core.plugin.aot.util.BoundedInputStream;
import org.libprunus.core.plugin.aot.util.ResourceLimitExceededException;

@DisableCachingByDefault(because = "Validates packaged provider binding output")
public abstract class VerifyPackagedProviderBindingTask extends DefaultTask {

    private static final long MAX_SPI_ENTRY_BYTES = 1024L * 1024L;
    private static final int MAX_SPI_LINE_LENGTH = 8192;

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArchiveFile();

    @Input
    public abstract Property<String> getBindingId();

    @Input
    @Optional
    public abstract Property<String> getExplicitBindingClass();

    @TaskAction
    public void verify() throws IOException {
        String archivePath = getArchiveFile().get().getAsFile().getAbsolutePath();
        String bindingId = getBindingId().get();
        String defaultBinding = BindingClassSelector.defaultBindingClassName(bindingId);
        String explicitBinding =
                PrunusStringUtils.normalize(getExplicitBindingClass().getOrNull());
        BindingClassSelector.SelectionResult selected = BindingClassSelector.select(explicitBinding, defaultBinding);
        VerifyContext ctx = new VerifyContext(archivePath, bindingId, selected.bindingClassName());

        String selectedClassEntry = selected.bindingClassName().replace('.', '/') + ".class";
        String callsiteClassEntry =
                RuntimeBindingCallsiteGenerator.callsiteClassName(bindingId).replace('.', '/') + ".class";
        String spiEntry = PrunusPluginConstants.SPI_SERVICES_DIR + "/" + PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN;

        try (ZipFile zipFile = new ZipFile(getArchiveFile().get().getAsFile())) {
            requireEntry(zipFile, selectedClassEntry, ctx, "Packaged binding class must be present");
            requireEntry(zipFile, callsiteClassEntry, ctx, "Packaged runtime binding callsite class must be present");
            ZipEntry targetSpiEntry = requireEntry(zipFile, spiEntry, ctx, "Packaged SPI entry must be present");

            List<String> providers = readProviders(zipFile, targetSpiEntry, ctx);
            if (providers.size() != 1) {
                throw ctx.failure("Packaged SPI provider list must contain exactly one entry but found "
                        + providers.size()
                        + ", providers="
                        + providers);
            }
            if (!selected.bindingClassName().equals(providers.get(0))) {
                throw ctx.failure("Packaged SPI provider must equal selected binding class but was "
                        + providers.get(0)
                        + ", providers="
                        + providers);
            }

            getLogger()
                    .lifecycle(
                            "Packaged provider binding verified: archive={}, bindingId={}, selectedBinding={}, explicitOverride={}, callsiteClass={}, spiProvider={}",
                            archivePath,
                            bindingId,
                            selected.bindingClassName(),
                            selected.explicit(),
                            callsiteClassEntry,
                            providers.get(0));
        }
    }

    private static ZipEntry requireEntry(ZipFile zipFile, String entryName, VerifyContext ctx, String detail) {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            entry = zipFile.getEntry("BOOT-INF/classes/" + entryName);
        }
        if (entry == null) {
            throw ctx.failure(detail);
        }
        return entry;
    }

    private static List<String> readProviders(ZipFile zipFile, ZipEntry entry, VerifyContext ctx) throws IOException {
        long entrySize = entry.getSize();
        if (entrySize > MAX_SPI_ENTRY_BYTES) {
            throw ctx.failure("Packaged SPI entry is too large: entry="
                    + entry.getName()
                    + ", size="
                    + entrySize
                    + ", max="
                    + MAX_SPI_ENTRY_BYTES);
        }

        List<String> providers = new ArrayList<>();
        try (InputStream raw = zipFile.getInputStream(entry);
                InputStream bounded = new BoundedInputStream(raw, MAX_SPI_ENTRY_BYTES, "Packaged SPI entry");
                BufferedReader reader = new BufferedReader(new InputStreamReader(bounded, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > MAX_SPI_LINE_LENGTH) {
                    throw ctx.failure("Packaged SPI entry line is too long: entry="
                            + entry.getName()
                            + ", lineLength="
                            + line.length()
                            + ", max="
                            + MAX_SPI_LINE_LENGTH);
                }
                int commentIndex = line.indexOf('#');
                if (commentIndex >= 0) {
                    line = line.substring(0, commentIndex);
                }
                String normalized = line.trim();
                if (!normalized.isEmpty()) {
                    providers.add(normalized);
                }
            }
        } catch (IOException e) {
            if (e instanceof ResourceLimitExceededException) {
                throw ctx.failure("Packaged SPI entry exceeded max bytes while reading: entry="
                        + entry.getName()
                        + ", max="
                        + MAX_SPI_ENTRY_BYTES);
            }
            throw e;
        }
        return providers;
    }

    private record VerifyContext(String archivePath, String bindingId, String selectedBinding) {

        private GradleException failure(String detail) {
            return new GradleException("Packaged provider binding verification failed: archive="
                    + archivePath
                    + ", bindingId="
                    + bindingId
                    + ", selectedBinding="
                    + selectedBinding
                    + ", detail="
                    + detail);
        }
    }
}
