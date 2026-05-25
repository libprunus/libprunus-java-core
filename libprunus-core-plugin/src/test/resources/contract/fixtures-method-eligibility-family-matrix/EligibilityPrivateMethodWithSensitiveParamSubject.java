package contract;

import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilityPrivateMethodWithSensitiveParamSubject {

    public String drivePrivate(String value) {
        return privateMethod(value);
    }

    @SuppressWarnings("unused")
    private String privateMethod(@Sensitive String secret) {
        return secret;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            EligibilityPrivateMethodWithSensitiveParamSubject instance =
                    new EligibilityPrivateMethodWithSensitiveParamSubject();
            CallsiteCapture.step(boundary, "drivePrivateCall", () -> instance.drivePrivate("arg-secret"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
