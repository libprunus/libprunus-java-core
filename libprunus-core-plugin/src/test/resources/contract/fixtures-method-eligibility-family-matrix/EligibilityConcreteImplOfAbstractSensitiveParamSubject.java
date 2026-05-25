package contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilityConcreteImplOfAbstractSensitiveParamSubject
        extends EligibilityAbstractMethodWithSensitiveParamAnchor {

    @Override
    public String handle(String secret) {
        return secret;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            EligibilityConcreteImplOfAbstractSensitiveParamSubject instance =
                    new EligibilityConcreteImplOfAbstractSensitiveParamSubject();
            CallsiteCapture.step(boundary, "handleCall", () -> instance.handle("arg-secret"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
