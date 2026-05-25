package contract;

import org.libprunus.core.log.annotation.DoNotLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilityObjectHashCodeOverrideWithDoNotLogSubject {

    private final int seed;

    public EligibilityObjectHashCodeOverrideWithDoNotLogSubject() {
        this(7);
    }

    public EligibilityObjectHashCodeOverrideWithDoNotLogSubject(int seed) {
        this.seed = seed;
    }

    @DoNotLog
    @Override
    public int hashCode() {
        return seed;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EligibilityObjectHashCodeOverrideWithDoNotLogSubject
                && ((EligibilityObjectHashCodeOverrideWithDoNotLogSubject) other).seed == this.seed;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            EligibilityObjectHashCodeOverrideWithDoNotLogSubject instance =
                    new EligibilityObjectHashCodeOverrideWithDoNotLogSubject();
            CallsiteCapture.step(boundary, "hashCodeCall", () -> instance.hashCode());
            boundary.info("===BOUNDARY END===");
        });
    }
}
