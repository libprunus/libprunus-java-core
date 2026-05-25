package contract;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;
import org.libprunus.core.log.annotation.DoNotLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DoNotLog
public class AutomatedMethodIgnoreOverridesClassDoNotLogSubject {

    @AutomatedProcessingIgnore
    public String ignored(String x) {
        return x + "-ignored-result";
    }

    public String tracked(String x) {
        return x + "-tracked-result";
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            AutomatedMethodIgnoreOverridesClassDoNotLogSubject instance =
                    new AutomatedMethodIgnoreOverridesClassDoNotLogSubject();
            CallsiteCapture.step(boundary, "ignoredCall", () -> instance.ignored("arg-ignored"));
            CallsiteCapture.step(boundary, "trackedCall", () -> instance.tracked("arg-tracked"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
