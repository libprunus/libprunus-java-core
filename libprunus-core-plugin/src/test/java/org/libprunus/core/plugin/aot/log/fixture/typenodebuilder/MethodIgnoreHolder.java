package org.libprunus.core.plugin.aot.log.fixture.typenodebuilder;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;

@SuppressWarnings("unused")
public class MethodIgnoreHolder {

    @AutomatedProcessingIgnore
    public void ignored() {}

    public void regular() {}
}
