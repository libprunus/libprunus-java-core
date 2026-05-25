package contract;

import org.libprunus.core.log.annotation.DoNotLog;

@DoNotLog
public class SameLayerConflictDoLogParent {
    public String parentMarker() {
        return "parent";
    }

    public String commonOp() {
        return "parent-op";
    }
}
