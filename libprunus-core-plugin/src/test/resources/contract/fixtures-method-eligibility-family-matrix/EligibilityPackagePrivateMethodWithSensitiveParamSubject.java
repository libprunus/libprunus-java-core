package contract;

import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilityPackagePrivateMethodWithSensitiveParamSubject {

    String packagePrivateMethod(@Sensitive String secret) {
        return secret;
    }

    public String drivePackagePrivate(String value) {
        return packagePrivateMethod(value);
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            EligibilityPackagePrivateMethodWithSensitiveParamSubject instance =
                    new EligibilityPackagePrivateMethodWithSensitiveParamSubject();
            CallsiteCapture.step(boundary, "drivePackagePrivateCall", () -> instance.drivePackagePrivate("arg-secret"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
