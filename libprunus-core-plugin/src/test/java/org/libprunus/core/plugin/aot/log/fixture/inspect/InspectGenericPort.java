package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.Sensitive;

public interface InspectGenericPort<T> {

    @Sensitive
    T process(T input);
}
