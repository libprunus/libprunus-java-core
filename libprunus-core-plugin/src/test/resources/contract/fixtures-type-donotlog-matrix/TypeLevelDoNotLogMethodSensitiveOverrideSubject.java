package contract;

import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DoNotLog
public class TypeLevelDoNotLogMethodSensitiveOverrideSubject {

    @Sensitive
    public String describe(String masked) {
        return masked;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            TypeLevelDoNotLogMethodSensitiveOverrideSubject instance =
                    new TypeLevelDoNotLogMethodSensitiveOverrideSubject();
            CallsiteCapture.step(boundary, "describeCall", () -> instance.describe("arg-masked"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
