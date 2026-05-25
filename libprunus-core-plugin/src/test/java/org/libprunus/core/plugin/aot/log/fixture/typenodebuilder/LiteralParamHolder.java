package org.libprunus.core.plugin.aot.log.fixture.typenodebuilder;

import org.libprunus.core.log.annotation.Sensitive;

@SuppressWarnings("unused")
public class LiteralParamHolder {

    public void withSensitiveParam(@Sensitive String value) {}

    public void noFamilyParam(String value) {}
}
