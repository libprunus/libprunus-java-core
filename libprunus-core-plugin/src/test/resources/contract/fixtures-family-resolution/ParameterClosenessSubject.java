package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Sensitive
public class ParameterClosenessSubject {

    @DoLog
    public String act(@DoNotLog String p1, String p2) {
        return p2;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            ParameterClosenessSubject inst = new ParameterClosenessSubject();
            CallsiteCapture.step(boundary, "act", () -> inst.act("arg-p1", "arg-p2"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
