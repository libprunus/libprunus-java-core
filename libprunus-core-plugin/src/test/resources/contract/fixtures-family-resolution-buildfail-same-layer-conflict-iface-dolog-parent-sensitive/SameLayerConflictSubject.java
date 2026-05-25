package contract;

public class SameLayerConflictSubject extends SameLayerConflictDoLogParent
        implements SameLayerConflictSensitiveInterface {

    @Override
    public String ifaceMarker() {
        return "iface";
    }

    @Override
    public String commonOp() {
        return "subject-op";
    }

    public String process(String x) {
        return x;
    }
}
