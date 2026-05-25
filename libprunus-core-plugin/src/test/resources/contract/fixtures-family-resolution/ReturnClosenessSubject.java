package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Sensitive
public class ReturnClosenessSubject {

    @DoLog
    public String compute(String x) {
        return x;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            ReturnClosenessSubject inst = new ReturnClosenessSubject();
            CallsiteCapture.step(boundary, "compute", () -> inst.compute("arg-x"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
