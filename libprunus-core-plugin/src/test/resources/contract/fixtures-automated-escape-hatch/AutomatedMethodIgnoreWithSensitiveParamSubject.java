package contract;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutomatedMethodIgnoreWithSensitiveParamSubject {

    @AutomatedProcessingIgnore
    public String ignored(@Sensitive String secret, @DoNotLog String hidden) {
        return secret + "/" + hidden;
    }

    public String tracked(@Sensitive String secret, String plain) {
        return secret + "/" + plain;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            AutomatedMethodIgnoreWithSensitiveParamSubject instance =
                    new AutomatedMethodIgnoreWithSensitiveParamSubject();
            CallsiteCapture.step(boundary, "ignoredCall", () -> instance.ignored("arg-secret", "arg-hidden"));
            CallsiteCapture.step(boundary, "trackedCall", () -> instance.tracked("arg-secret", "arg-plain"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
