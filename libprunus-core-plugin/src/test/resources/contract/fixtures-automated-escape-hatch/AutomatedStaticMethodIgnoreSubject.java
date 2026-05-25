package contract;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutomatedStaticMethodIgnoreSubject {

    @AutomatedProcessingIgnore
    public static String ignoredStatic(@Sensitive String secret) {
        return "static:" + secret;
    }

    public String trackedInstance(String x) {
        return x + "-tracked-result";
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            AutomatedStaticMethodIgnoreSubject instance = new AutomatedStaticMethodIgnoreSubject();
            CallsiteCapture.step(
                    boundary,
                    "ignoredStaticCall",
                    () -> AutomatedStaticMethodIgnoreSubject.ignoredStatic("arg-secret"));
            CallsiteCapture.step(boundary, "trackedInstanceCall", () -> instance.trackedInstance("arg-tracked"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
