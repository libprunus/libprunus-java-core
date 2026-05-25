package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.DoNotLog;

public class InspectIgnoredFieldDto {

    public String visible;

    @DoNotLog
    public String hidden;
}
