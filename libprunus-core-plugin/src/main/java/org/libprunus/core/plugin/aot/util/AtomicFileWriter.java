package org.libprunus.core.plugin.aot.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public final class AtomicFileWriter {

    private AtomicFileWriter() {
        throw new UnsupportedOperationException();
    }

    public static void writeIfChanged(Path target, byte[] content) throws IOException {
        if (Files.exists(target) && Files.size(target) == content.length) {
            byte[] existing = Files.readAllBytes(target);
            if (Arrays.equals(existing, content)) {
                return;
            }
        }
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(tmp, content);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static void writeIfChanged(Path target, String content, Charset charset) throws IOException {
        writeIfChanged(target, content.getBytes(charset));
    }
}
