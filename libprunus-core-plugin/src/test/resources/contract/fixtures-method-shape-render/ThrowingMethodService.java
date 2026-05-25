package contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThrowingMethodService {

    public String throwing(String x) {
        throw new IllegalStateException("bang: " + x);
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            ThrowingMethodService instance = new ThrowingMethodService();
            boundary.info("===BOUNDARY throwing===");
            try {
                instance.throwing("arg-throwing");
                boundary.info("===NO-THROW===");
            } catch (RuntimeException ex) {
                boundary.info("===CAUGHT " + ex.getClass().getName() + ": " + ex.getMessage() + "===");
            }
            boundary.info("===BOUNDARY END===");
        });
    }
}
