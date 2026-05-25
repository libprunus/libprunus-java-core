package contract;

import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DoNotLog
public class TypeLevelDoNotLogParamSensitiveOverrideSubject {

    public String describe(@Sensitive String masked) {
        return masked;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            TypeLevelDoNotLogParamSensitiveOverrideSubject instance =
                    new TypeLevelDoNotLogParamSensitiveOverrideSubject();
            CallsiteCapture.step(boundary, "describeCall", () -> instance.describe("arg-masked"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
