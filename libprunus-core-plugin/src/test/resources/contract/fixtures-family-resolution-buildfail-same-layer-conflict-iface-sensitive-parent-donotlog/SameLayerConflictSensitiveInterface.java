package contract;

import org.libprunus.core.log.annotation.Sensitive;

@Sensitive
public interface SameLayerConflictSensitiveInterface {
    String ifaceMarker();

    String commonOp();
}
