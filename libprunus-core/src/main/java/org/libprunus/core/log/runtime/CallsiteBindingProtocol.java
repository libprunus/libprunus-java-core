package org.libprunus.core.log.runtime;

public final class CallsiteBindingProtocol {

    public static final String RESOURCE_DIR = "META-INF/prunus/aot";
    public static final String RESOURCE_FILENAME = "runtime-binding-callsite";
    public static final String RESOURCE_PATH = RESOURCE_DIR + "/" + RESOURCE_FILENAME;

    private CallsiteBindingProtocol() {
        throw new UnsupportedOperationException();
    }
}
