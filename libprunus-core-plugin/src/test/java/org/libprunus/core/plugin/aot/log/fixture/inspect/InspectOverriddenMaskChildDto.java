package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.DoLog;

@DoLog
public class InspectOverriddenMaskChildDto extends InspectMaskedParentDto {

    public String childField;
}
