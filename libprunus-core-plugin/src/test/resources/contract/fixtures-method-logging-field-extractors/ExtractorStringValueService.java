package contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractorStringValueService {

    public String run(String x) {
        return x + "-result";
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            ExtractorStringValueService instance = new ExtractorStringValueService();
            CallsiteCapture.step(boundary, "runCall", () -> instance.run("arg-run"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
