package org.libprunus.core.log.runtime;

final class IdentityRenderer implements TypeRenderer {

    static final IdentityRenderer INSTANCE = new IdentityRenderer();
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private IdentityRenderer() {}

    @Override
    public void render(StringBuilderWithContext context, Object value) {
        if (!context.append(value.getClass().getName())) {
            return;
        }
        if (!context.append('@')) {
            return;
        }
        appendHashCodeHex(context, System.identityHashCode(value));
    }

    private static void appendHashCodeHex(StringBuilderWithContext context, int hashCode) {
        int leadingZeros = Integer.numberOfLeadingZeros(hashCode);
        int firstShift = Math.max(0, 28 - (leadingZeros & ~3));
        for (int shift = firstShift; shift >= 0; shift -= 4) {
            if (!context.append(HEX_DIGITS[(hashCode >> shift) & 0xF])) {
                return;
            }
        }
    }
}
