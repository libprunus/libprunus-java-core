package org.libprunus.core.plugin.aot.log.fixture.typenodebuilder;

import org.libprunus.core.log.annotation.DoNotLog;

@SuppressWarnings("unused")
public class RootWithSuppressedField {

    @DoNotLog
    public int suppressed;

    public int kept;
}
