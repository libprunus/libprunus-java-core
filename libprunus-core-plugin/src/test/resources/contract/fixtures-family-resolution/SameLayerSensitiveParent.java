package contract;

import org.libprunus.core.log.annotation.Sensitive;

@Sensitive
public class SameLayerSensitiveParent {
    public String parentMarker() {
        return "parent";
    }
}
