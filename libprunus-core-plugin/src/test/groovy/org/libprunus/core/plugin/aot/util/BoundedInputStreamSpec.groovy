package org.libprunus.core.plugin.aot.util

import spock.lang.Specification

class BoundedInputStreamSpec extends Specification {

    def "ctor rejects negative maxBytes with IllegalArgumentException carrying the offending value"() {
        given:
        def source = new ByteArrayInputStream(new byte[0])

        when:
        new BoundedInputStream(source, invalidMax, "Test resource")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == expectedMessage

        where:
        invalidMax     || expectedMessage
        -1L            || "maxBytes must be >= 0: -1"
        Long.MIN_VALUE || "maxBytes must be >= 0: -9223372036854775808"
    }

    def "ctor rejects null contextMessage with NullPointerException whose message names the parameter"() {
        given:
        def source = new ByteArrayInputStream(new byte[0])

        when:
        new BoundedInputStream(source, 1L, null)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "contextMessage"
    }

    def "read single byte returns value and increments consumed below budget"() {
        given:
        def source = new ByteArrayInputStream(new byte[]{0x42 as byte})
        def bounded = new BoundedInputStream(source, 5L, "Test resource")
        bounded.@consumed = 2L

        when:
        int result = bounded.read()

        then:
        result == 0x42
        bounded.@consumed == 3L
    }

    def "read single byte returns minus one when underlying stream is at EOF below budget"() {
        given:
        def source = new ByteArrayInputStream(new byte[0])
        def bounded = new BoundedInputStream(source, 5L, "Test resource")
        bounded.@consumed = 2L

        when:
        int result = bounded.read()

        then:
        result == -1
        bounded.@consumed == 2L
    }

    def "read single byte throws ResourceLimitExceededException with formatted message when limit reached and underlying has data"() {
        given:
        def source = new ByteArrayInputStream(new byte[5])
        def bounded = new BoundedInputStream(source, 5L, "Whitelist resource")
        bounded.@consumed = 5L

        when:
        bounded.read()

        then:
        def ex = thrown(ResourceLimitExceededException)
        ex.message == "Whitelist resource exceeds max bytes: consumed=5 >= 5"
        bounded.@consumed == 5L
    }

    def "read single byte returns minus one when consumed equals maxBytes and underlying stream is at EOF"() {
        given:
        def source = new ByteArrayInputStream(new byte[0])
        def bounded = new BoundedInputStream(source, 5L, "Test resource")
        bounded.@consumed = 5L

        when:
        int result = bounded.read()

        then:
        result == -1
        bounded.@consumed == 5L
    }

    def "read array throws NullPointerException when buffer is null regardless of remaining budget"() {
        given:
        def source = new ByteArrayInputStream(new byte[5])
        def bounded = new BoundedInputStream(source, 10L, "Test resource")
        bounded.@consumed = consumed

        when:
        bounded.read(null, 0, 1)

        then:
        thrown(NullPointerException)

        where:
        consumed << [0L, 5L, 10L]
    }

    def "read array throws IndexOutOfBoundsException for invalid off or len regardless of remaining budget"() {
        given:
        def source = new ByteArrayInputStream(new byte[5])
        def bounded = new BoundedInputStream(source, 10L, "Test resource")
        bounded.@consumed = consumed

        when:
        bounded.read(new byte[4], off, len)

        then:
        thrown(IndexOutOfBoundsException)

        where:
        consumed | off | len
        0L       | -1  | 1
        0L       | 0   | -1
        0L       | 0   | 5
        10L      | -1  | 1
        10L      | 0   | -1
        10L      | 0   | 5
    }

    def "read array returns zero immediately when len is zero without accessing underlying stream"() {
        given:
        def source = new InputStream() {
            @Override int read() { throw new AssertionError("stream must not be accessed") }
            @Override int read(byte[] b, int off, int len) { throw new AssertionError("stream must not be accessed") }
        }
        def bounded = new BoundedInputStream(source, 5L, "Test resource")
        def buffer = new byte[4]

        when:
        int result = bounded.read(buffer, 0, 0)

        then:
        result == 0
        buffer == new byte[4]
        bounded.@consumed == 0L
    }

    def "read array throws ResourceLimitExceededException with formatted message when limit reached and underlying has data"() {
        given:
        def source = new ByteArrayInputStream(new byte[10])
        def bounded = new BoundedInputStream(source, 5L, "Whitelist resource")
        bounded.@consumed = 5L

        when:
        bounded.read(new byte[4], 0, 4)

        then:
        def ex = thrown(ResourceLimitExceededException)
        ex.message == "Whitelist resource exceeds max bytes: consumed=5 >= 5"
        bounded.@consumed == 5L
    }

    def "read array does not modify caller buffer when limit is reached and underlying stream has data"() {
        given:
        def source = new ByteArrayInputStream(new byte[]{0x55 as byte})
        def bounded = new BoundedInputStream(source, 5L, "Test resource")
        bounded.@consumed = 5L
        def buffer = new byte[4]
        Arrays.fill(buffer, (byte) 0xAA)

        when:
        bounded.read(buffer, 0, 4)

        then:
        thrown(ResourceLimitExceededException)
        buffer == [(byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA] as byte[]
        bounded.@consumed == 5L
    }

    def "read array reads into buffer at non-zero offset and increments consumed by actual count when budget exceeds requested length"() {
        given:
        def source = new ByteArrayInputStream([1, 2, 3] as byte[])
        def bounded = new BoundedInputStream(source, 100L, "Test resource")
        def buffer = new byte[5]

        when:
        int count = bounded.read(buffer, 2, 3)

        then:
        count == 3
        buffer == [0, 0, 1, 2, 3] as byte[]
        bounded.@consumed == 3L
    }

    def "read array returns minus one and leaves consumed unchanged when underlying stream signals EOF with budget remaining"() {
        given:
        def source = new ByteArrayInputStream(new byte[0])
        def bounded = new BoundedInputStream(source, 10L, "Test resource")
        bounded.@consumed = 3L

        when:
        int count = bounded.read(new byte[4], 0, 4)

        then:
        count == -1
        bounded.@consumed == 3L
    }

    def "read array clamps requested length to remaining budget so underlying stream receives pre-clamped len"() {
        given:
        def actualReadLengths = []
        def source = new InputStream() {
            @Override int read() { return 0 }
            @Override int read(byte[] b, int off, int len) {
                actualReadLengths << len
                Arrays.fill(b, off, off + len, (byte) 0x41)
                return len
            }
        }
        def bounded = new BoundedInputStream(source, 8L, "Test resource")
        bounded.@consumed = 5L

        when:
        int count = bounded.read(new byte[6], 0, 6)

        then:
        actualReadLengths == [3]
        count == 3
        bounded.@consumed == 8L
    }

    def "read array advances consumed by actual count not requested len when underlying performs short read"() {
        given:
        def actualReadLengths = []
        def source = new InputStream() {
            @Override int read() { return 0 }
            @Override int read(byte[] b, int off, int len) {
                actualReadLengths << len
                int shortCount = len / 2
                Arrays.fill(b, off, off + shortCount, (byte) 0x33)
                return shortCount
            }
        }
        def bounded = new BoundedInputStream(source, 100L, "Test resource")
        def buffer = new byte[8]

        when:
        int count = bounded.read(buffer, 0, 8)

        then:
        actualReadLengths == [8]
        count == 4
        bounded.@consumed == 4L
        buffer == [0x33, 0x33, 0x33, 0x33, 0, 0, 0, 0] as byte[]
    }

    def "read array returns minus one when consumed equals maxBytes and underlying stream is at EOF"() {
        given:
        def source = new ByteArrayInputStream(new byte[0])
        def bounded = new BoundedInputStream(source, 5L, "Test resource")
        bounded.@consumed = 5L

        when:
        int result = bounded.read(new byte[4], 0, 4)

        then:
        result == -1
        bounded.@consumed == 5L
    }

    def "read array exhausts quota then next read throws ResourceLimitExceededException"() {
        given:
        def source = new ByteArrayInputStream(new byte[10])
        def bounded = new BoundedInputStream(source, 5L, "Whitelist resource")

        when:
        int first = bounded.read(new byte[5], 0, 5)
        bounded.read(new byte[1], 0, 1)

        then:
        first == 5
        bounded.@consumed == 5L
        def ex = thrown(ResourceLimitExceededException)
        ex.message == "Whitelist resource exceeds max bytes: consumed=5 >= 5"
    }

    def "skip clamps requested length to remaining budget so underlying stream receives pre-clamped n"() {
        given:
        def actualSkipN = []
        def source = new InputStream() {
            @Override int read() { return 0 }
            @Override long skip(long n) { actualSkipN << n; return n }
        }
        def bounded = new BoundedInputStream(source, 8L, "Test resource")
        bounded.@consumed = 5L

        when:
        long skipped = bounded.skip(6)

        then:
        actualSkipN == [3L]
        skipped == 3L
        bounded.@consumed == 8L
    }

    def "skip leaves consumed unchanged when underlying stream returns zero skipped"() {
        given:
        def source = new InputStream() {
            @Override int read() { return -1 }
            @Override long skip(long n) { return 0L }
        }
        def bounded = new BoundedInputStream(source, 8L, "Test resource")
        bounded.@consumed = 2L

        when:
        long skipped = bounded.skip(4L)

        then:
        skipped == 0L
        bounded.@consumed == 2L
    }

    def "skip returns zero when underlying stream misbehaves and returns a negative skipped count"() {
        given:
        def actualSkipN = []
        def source = new InputStream() {
            @Override int read() { return -1 }
            @Override long skip(long n) { actualSkipN << n; return -1L }
        }
        def bounded = new BoundedInputStream(source, 8L, "Test resource")
        bounded.@consumed = 2L

        when:
        long skipped = bounded.skip(4L)

        then:
        skipped == 0L
        bounded.@consumed == 2L
        actualSkipN == [4L]
    }

    def "skip with non-positive n returns zero without accessing underlying stream"() {
        given:
        def source = new InputStream() {
            @Override int read() { throw new AssertionError("stream must not be accessed") }
            @Override long skip(long n) { throw new AssertionError("stream must not be accessed") }
        }
        def bounded = new BoundedInputStream(source, 5L, "Test resource")

        when:
        long result = bounded.skip(skipN)

        then:
        result == 0L

        where:
        skipN << [0L, -1L, Long.MIN_VALUE]
    }

    def "skip throws ResourceLimitExceededException with formatted message when limit reached and underlying has data"() {
        given:
        def source = new ByteArrayInputStream(new byte[5])
        def bounded = new BoundedInputStream(source, 5L, "Whitelist resource")
        bounded.@consumed = 5L

        when:
        bounded.skip(1)

        then:
        def ex = thrown(ResourceLimitExceededException)
        ex.message == "Whitelist resource exceeds max bytes: consumed=5 >= 5"
        bounded.@consumed == 5L
    }

    def "skip returns zero when consumed equals maxBytes and underlying stream is at EOF"() {
        given:
        def source = new ByteArrayInputStream(new byte[0])
        def bounded = new BoundedInputStream(source, 5L, "Test resource")
        bounded.@consumed = 5L

        when:
        long result = bounded.skip(1)

        then:
        result == 0L
        bounded.@consumed == 5L
    }

    def "available() returns the minimum of remaining budget and underlying stream's available"() {
        given:
        def avail = underlyingAvailable
        def source = new InputStream() {
            @Override int read() { return 0 }
            @Override int available() { return avail }
        }
        def bounded = new BoundedInputStream(source, maxBytes, "Test resource")
        bounded.@consumed = consumed

        when:
        int result = bounded.available()

        then:
        result == expected

        where:
        consumed | maxBytes | underlyingAvailable || expected
        5L       | 8L       | 100                 || 3
        5L       | 8L       | 2                   || 2
        8L       | 8L       | 100                 || 0
    }

    def "available() returns 0 after a real read(byte[],...) exhausts the quota"() {
        given:
        def source = new ByteArrayInputStream(new byte[10])
        def bounded = new BoundedInputStream(source, 3L, "Test resource")
        bounded.read(new byte[3], 0, 3)

        when:
        int result = bounded.available()

        then:
        result == 0
    }

    def "mark() does not delegate to the underlying stream"() {
        given:
        def markInvokedWith = []
        def source = new InputStream() {
            @Override int read() { return -1 }
            @Override void mark(int readlimit) { markInvokedWith << readlimit }
            @Override boolean markSupported() { return true }
        }
        def bounded = new BoundedInputStream(source, 5L, "Test resource")

        when:
        bounded.mark(100)

        then:
        markInvokedWith.isEmpty()
    }

    def "reset() throws IOException and does not roll back the underlying stream position"() {
        given:
        def source = new ByteArrayInputStream([10, 20, 30, 40, 50, 60] as byte[])
        def bounded = new BoundedInputStream(source, 10L, "Test resource")
        bounded.read(new byte[3], 0, 3)

        when:
        bounded.reset()

        then:
        def ex = thrown(IOException)
        ex.message == "mark/reset not supported by BoundedInputStream"
        source.available() == 3
    }

    def "markSupported() returns false regardless of underlying capability"() {
        given:
        def source = new InputStream() {
            @Override int read() { return -1 }
            @Override boolean markSupported() { return true }
        }
        def bounded = new BoundedInputStream(source, 5L, "Test resource")

        when:
        boolean result = bounded.markSupported()

        then:
        !result
        source.markSupported()
    }
}
