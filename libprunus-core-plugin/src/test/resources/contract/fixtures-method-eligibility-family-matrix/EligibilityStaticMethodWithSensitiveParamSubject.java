package contract;

import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilityStaticMethodWithSensitiveParamSubject {

    public static String staticWithSensitiveParam(@Sensitive String secret) {
        return secret;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            CallsiteCapture.step(
                    boundary,
                    "staticCall",
                    () -> EligibilityStaticMethodWithSensitiveParamSubject.staticWithSensitiveParam("arg-secret"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
