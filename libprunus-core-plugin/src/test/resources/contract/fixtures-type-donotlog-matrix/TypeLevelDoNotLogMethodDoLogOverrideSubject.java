package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DoNotLog
public class TypeLevelDoNotLogMethodDoLogOverrideSubject {

    @DoLog
    public String describe(String visible) {
        return visible;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            TypeLevelDoNotLogMethodDoLogOverrideSubject instance = new TypeLevelDoNotLogMethodDoLogOverrideSubject();
            CallsiteCapture.step(boundary, "describeCall", () -> instance.describe("arg-visible"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
