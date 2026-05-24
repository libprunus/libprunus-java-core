package org.libprunus.core.plugin.aot;

public enum AotMode {
    /** Emit binding class + SPI metadata for the host application jar; whitelist comes from registry. */
    APPLICATION,
    /** Emit aggregated whitelist file for downstream consumers; no SPI binding class. */
    LIBRARY
}
