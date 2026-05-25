package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilityConstructorWithDoLogParamSubject {

    private final String value;

    public EligibilityConstructorWithDoLogParamSubject(@DoLog String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            CallsiteCapture.step(
                    boundary, "constructorCall", () -> new EligibilityConstructorWithDoLogParamSubject("arg-value"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
