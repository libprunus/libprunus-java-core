package org.libprunus.core.plugin.aot.log.fixture.pojo;

import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

@DoNotLog
public class MaskIgnoreDto {

    public String hidden;

    @Sensitive
    public String v;
}
