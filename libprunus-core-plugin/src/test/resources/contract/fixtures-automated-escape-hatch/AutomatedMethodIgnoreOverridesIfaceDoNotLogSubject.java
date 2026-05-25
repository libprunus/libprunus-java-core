package contract;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutomatedMethodIgnoreOverridesIfaceDoNotLogSubject implements AutomatedIgnoreIfaceDoNotLogAnchor {

    @AutomatedProcessingIgnore
    @Override
    public String run(String x) {
        return x + "-run-result";
    }

    public String tracked(String x) {
        return x + "-tracked-result";
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            AutomatedMethodIgnoreOverridesIfaceDoNotLogSubject instance =
                    new AutomatedMethodIgnoreOverridesIfaceDoNotLogSubject();
            CallsiteCapture.step(boundary, "runCall", () -> instance.run("arg-run"));
            CallsiteCapture.step(boundary, "trackedCall", () -> instance.tracked("arg-tracked"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
