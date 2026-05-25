package contract;

import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilityStaticFactoryWithSensitiveParamSubject {

    private final String value;

    private EligibilityStaticFactoryWithSensitiveParamSubject(String value) {
        this.value = value;
    }

    public static EligibilityStaticFactoryWithSensitiveParamSubject of(@Sensitive String secret) {
        return new EligibilityStaticFactoryWithSensitiveParamSubject(secret);
    }

    public String getValue() {
        return value;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            CallsiteCapture.step(
                    boundary, "factoryCall", () -> EligibilityStaticFactoryWithSensitiveParamSubject.of("arg-secret"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
