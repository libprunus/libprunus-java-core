package contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoidMethodService {

    public void doVoid(String x) {}

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            VoidMethodService instance = new VoidMethodService();
            CallsiteCapture.stepVoid(boundary, "doVoid", () -> instance.doVoid("arg-doVoid"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
