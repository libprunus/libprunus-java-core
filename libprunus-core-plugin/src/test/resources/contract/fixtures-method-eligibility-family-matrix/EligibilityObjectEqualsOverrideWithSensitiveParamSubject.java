package contract;

import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilityObjectEqualsOverrideWithSensitiveParamSubject {

    private final int seed;

    public EligibilityObjectEqualsOverrideWithSensitiveParamSubject() {
        this(42);
    }

    public EligibilityObjectEqualsOverrideWithSensitiveParamSubject(int seed) {
        this.seed = seed;
    }

    @Override
    public boolean equals(@Sensitive Object other) {
        return other instanceof EligibilityObjectEqualsOverrideWithSensitiveParamSubject
                && ((EligibilityObjectEqualsOverrideWithSensitiveParamSubject) other).seed == this.seed;
    }

    @Override
    public int hashCode() {
        return seed;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            EligibilityObjectEqualsOverrideWithSensitiveParamSubject instance =
                    new EligibilityObjectEqualsOverrideWithSensitiveParamSubject();
            EligibilityObjectEqualsOverrideWithSensitiveParamSubject other =
                    new EligibilityObjectEqualsOverrideWithSensitiveParamSubject();
            CallsiteCapture.step(boundary, "equalsCall", () -> instance.equals(other));
            boundary.info("===BOUNDARY END===");
        });
    }
}
