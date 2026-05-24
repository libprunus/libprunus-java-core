package org.libprunus.core.plugin.aot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ShortStableHash {

    static final int HASH_BYTES = 16;

    private ShortStableHash() {
        throw new UnsupportedOperationException();
    }

    static String of(String input) {
        byte[] digest = newSha256().digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest, 0, HASH_BYTES);
    }

    static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }
}
