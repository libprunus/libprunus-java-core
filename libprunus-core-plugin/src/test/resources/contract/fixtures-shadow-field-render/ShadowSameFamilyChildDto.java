package contract;

import org.libprunus.core.log.annotation.Sensitive;

public class ShadowSameFamilyChildDto extends ShadowSameFamilyParentDto {

    @Sensitive
    public String name = "child-val";

    public String childOnly = "child-only-val";
}
