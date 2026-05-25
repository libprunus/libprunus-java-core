package org.libprunus.core.plugin.aot.log.fixture.pojo;

import org.libprunus.core.log.annotation.Sensitive;

public class MixedDto {

    @Sensitive
    public String a;

    public String b;
}
