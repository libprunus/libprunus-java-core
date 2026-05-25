package contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SameLayerSensitiveSubject extends SameLayerSensitiveParent implements SameLayerSensitiveInterface {

    @Override
    public String ifaceMarker() {
        return "iface";
    }

    public String process(String x, String s) {
        return x;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            SameLayerSensitiveSubject inst = new SameLayerSensitiveSubject();
            CallsiteCapture.step(boundary, "process", () -> inst.process("arg-x", "arg-s"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
