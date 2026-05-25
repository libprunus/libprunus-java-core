package org.libprunus.core.plugin.aot.log.fixture.typenodebuilder;

import org.libprunus.core.log.annotation.Sensitive;

@SuppressWarnings("unused")
public class FieldFamilyHolder {

    @Sensitive
    public String masked;

    public String plain;
}
