package contract;

import org.libprunus.core.log.annotation.DoLog;

@DoLog
public interface SameLayerConflictSensitiveInterface {
    String ifaceMarker();

    String commonOp();
}
