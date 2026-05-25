package contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SensitiveInterfaceSubject implements SensitiveInterface {

    @Override
    public String act(String x, String s) {
        return x;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            SensitiveInterfaceSubject inst = new SensitiveInterfaceSubject();
            CallsiteCapture.step(boundary, "act", () -> inst.act("arg-x", "arg-s"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
