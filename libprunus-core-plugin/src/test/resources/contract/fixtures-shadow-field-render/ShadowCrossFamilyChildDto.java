package contract;

import org.libprunus.core.log.annotation.DoLog;

public class ShadowCrossFamilyChildDto extends ShadowCrossFamilyParentDto {

    @DoLog
    public String data = "child-val";
}
