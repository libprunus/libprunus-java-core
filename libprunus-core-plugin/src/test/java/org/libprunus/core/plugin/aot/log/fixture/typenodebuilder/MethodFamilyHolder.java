package org.libprunus.core.plugin.aot.log.fixture.typenodebuilder;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

@SuppressWarnings("unused")
public class MethodFamilyHolder {

    public void plain() {}

    @Sensitive
    public void masked() {}

    @DoNotLog
    public void suppressed() {}

    @DoLog
    public void passThrough() {}
}
