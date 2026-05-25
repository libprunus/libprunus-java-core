package contract;

import org.libprunus.core.log.annotation.DoLog;

@DoLog
public class SameLayerConflictDoLogParent {
    public String parentMarker() {
        return "parent";
    }

    public String commonOp() {
        return "parent-op";
    }
}
