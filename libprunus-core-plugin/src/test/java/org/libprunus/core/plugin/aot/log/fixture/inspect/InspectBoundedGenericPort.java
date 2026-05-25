package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.Sensitive;

public interface InspectBoundedGenericPort<T extends CharSequence> {

    @Sensitive
    T normalize(T input);
}
