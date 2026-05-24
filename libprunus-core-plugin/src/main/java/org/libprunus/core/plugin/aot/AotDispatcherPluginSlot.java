package org.libprunus.core.plugin.aot;

enum AotDispatcherPluginSlot {
    LOG(0);

    private final int bitIndex;

    AotDispatcherPluginSlot(int bitIndex) {
        this.bitIndex = bitIndex;
    }

    int bitMask() {
        return 1 << bitIndex;
    }
}
