package org.libprunus.core.plugin.aot.testutil;

import java.io.IOException;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;

public final class CloseFailurePlugin implements Plugin {

    private final Throwable throwable;

    public CloseFailurePlugin(Throwable throwable) {
        this.throwable = throwable;
    }

    @Override
    public boolean matches(TypeDescription target) {
        return false;
    }

    @Override
    public DynamicType.Builder<?> apply(
            DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassFileLocator classFileLocator) {
        return builder;
    }

    @Override
    public void close() throws IOException {
        if (throwable instanceof IOException ioException) {
            throw ioException;
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
    }
}
