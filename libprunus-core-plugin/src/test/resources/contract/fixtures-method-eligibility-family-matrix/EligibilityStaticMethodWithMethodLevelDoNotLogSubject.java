package contract;

import org.libprunus.core.log.annotation.DoNotLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilityStaticMethodWithMethodLevelDoNotLogSubject {

    @DoNotLog
    public static String staticDoNotLog(String value) {
        return value;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            CallsiteCapture.step(
                    boundary,
                    "staticCall",
                    () -> EligibilityStaticMethodWithMethodLevelDoNotLogSubject.staticDoNotLog("arg-value"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
