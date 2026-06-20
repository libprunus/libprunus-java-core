package org.libprunus.core.plugin.aot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class BindingIdGenerator {

    public String generate(
            @Nullable String group,
            @Nullable String artifact,
            @Nullable String version,
            String modulePath,
            String variant) {
        Objects.requireNonNull(modulePath, "modulePath");
        Objects.requireNonNull(variant, "variant");
        if (modulePath.isBlank()) {
            throw new IllegalArgumentException("modulePath must not be blank");
        }
        if (variant.isBlank()) {
            throw new IllegalArgumentException("variant must not be blank");
        }

        MessageDigest digest = ShortStableHash.newSha256();
        updateDigest(digest, group);
        digest.update((byte) 0);
        updateDigest(digest, artifact);
        digest.update((byte) 0);
        updateDigest(digest, version);
        digest.update((byte) 0);
        updateDigest(digest, modulePath);
        digest.update((byte) 0);
        updateDigest(digest, variant);

        return "b" + HexFormat.of().formatHex(digest.digest(), 0, ShortStableHash.HASH_BYTES);
    }

    private static void updateDigest(MessageDigest digest, @Nullable String value) {
        String text = (value == null) ? "" : value.strip();
        if (text.isEmpty()) {
            text = "unspecified";
        }
        digest.update(text.getBytes(StandardCharsets.UTF_8));
    }
}
