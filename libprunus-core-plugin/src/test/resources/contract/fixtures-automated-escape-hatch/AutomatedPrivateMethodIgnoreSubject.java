package contract;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class AutomatedPrivateMethodIgnoreSubject {

    @AutomatedProcessingIgnore
    private String ignoredPrivate(@Sensitive String secret) {
        return "private:" + secret;
    }

    public String trackedPublic(String x) {
        return x + "-tracked-result";
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            AutomatedPrivateMethodIgnoreSubject instance = new AutomatedPrivateMethodIgnoreSubject();
            CallsiteCapture.step(boundary, "trackedPublicCall", () -> instance.trackedPublic("arg-tracked"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
