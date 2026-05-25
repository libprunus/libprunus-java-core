package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilityObjectToStringOverrideWithDoLogSubject {

    @DoLog
    @Override
    public String toString() {
        return "EligibilityObjectToStringOverrideWithDoLogSubject()";
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            EligibilityObjectToStringOverrideWithDoLogSubject instance =
                    new EligibilityObjectToStringOverrideWithDoLogSubject();
            CallsiteCapture.step(boundary, "toStringCall", () -> instance.toString());
            boundary.info("===BOUNDARY END===");
        });
    }
}
