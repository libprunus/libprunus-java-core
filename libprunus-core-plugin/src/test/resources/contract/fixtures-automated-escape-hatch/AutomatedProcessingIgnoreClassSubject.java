package contract;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutomatedProcessingIgnore
public class AutomatedProcessingIgnoreClassSubject {

    public String secret = "secret-val";

    public String run(String x) {
        return x + "-result";
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            AutomatedProcessingIgnoreClassSubject instance = new AutomatedProcessingIgnoreClassSubject();
            CallsiteCapture.step(boundary, "runCall", () -> instance.run("arg-run"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
