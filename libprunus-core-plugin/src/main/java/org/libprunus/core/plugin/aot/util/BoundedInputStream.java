package org.libprunus.core.plugin.aot.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class BoundedInputStream extends FilterInputStream {

    private final long maxBytes;
    private final String contextMessage;
    private long consumed;

    public BoundedInputStream(InputStream in, long maxBytes, String contextMessage) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be >= 0: " + maxBytes);
        }
        Objects.requireNonNull(contextMessage, "contextMessage");
        super(in);
        this.maxBytes = maxBytes;
        this.contextMessage = contextMessage;
    }

    @Override
    public int read() throws IOException {
        if (consumed >= maxBytes) {
            int probe = super.read();
            if (probe < 0) return -1;
            throw limitExceeded();
        }
        int value = super.read();
        if (value >= 0) {
            consumed++;
        }
        return value;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        Objects.requireNonNull(b);
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return 0;
        }
        long remaining = maxBytes - consumed;
        if (remaining <= 0) {
            int probe = super.read();
            if (probe < 0) return -1;
            throw limitExceeded();
        }
        int safeLen = (int) Math.min(len, remaining);
        int count = super.read(b, off, safeLen);
        if (count > 0) {
            consumed += count;
        }
        return count;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) return 0;
        if (consumed >= maxBytes) {
            int probe = super.read();
            if (probe < 0) return 0;
            throw limitExceeded();
        }
        long safeSkip = Math.min(n, maxBytes - consumed);
        long skipped = super.skip(safeSkip);
        if (skipped > 0) {
            consumed += skipped;
        }
        return Math.max(0L, skipped);
    }

    @Override
    public int available() throws IOException {
        long remaining = maxBytes - consumed;
        if (remaining <= 0) {
            return 0;
        }
        return (int) Math.min(super.available(), remaining);
    }

    @Override
    public synchronized void mark(int readlimit) {
        // no-op: markSupported() == false; reset() throws IOException unconditionally
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported by BoundedInputStream");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    private ResourceLimitExceededException limitExceeded() {
        return new ResourceLimitExceededException(
                contextMessage + " exceeds max bytes: consumed=" + consumed + " >= " + maxBytes);
    }
}
