package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DoNotLog
public class TypeLevelDoNotLogParamDoLogOverrideSubject {

    public String describe(@DoLog String visible) {
        return visible;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            TypeLevelDoNotLogParamDoLogOverrideSubject instance = new TypeLevelDoNotLogParamDoLogOverrideSubject();
            CallsiteCapture.step(boundary, "describeCall", () -> instance.describe("arg-visible"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
