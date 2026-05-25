package contract;

import org.libprunus.core.log.annotation.DoNotLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilityProtectedMethodWithDoNotLogParamSubject {

    protected String protectedMethod(@DoNotLog String secret) {
        return secret;
    }

    public String driveProtected(String value) {
        return protectedMethod(value);
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            EligibilityProtectedMethodWithDoNotLogParamSubject instance =
                    new EligibilityProtectedMethodWithDoNotLogParamSubject();
            CallsiteCapture.step(boundary, "driveProtectedCall", () -> instance.driveProtected("arg-secret"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
