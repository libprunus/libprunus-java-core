package contract;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Sensitive
public class AutomatedMethodIgnoreSiblingFieldsRemainSensitiveSubject {

    public String fieldMasked = "secret-val";

    @AutomatedProcessingIgnore
    public String ignoredMethod(String x) {
        return x + "-ignored-result";
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            AutomatedMethodIgnoreSiblingFieldsRemainSensitiveSubject instance =
                    new AutomatedMethodIgnoreSiblingFieldsRemainSensitiveSubject();
            CallsiteCapture.step(boundary, "ignoredMethodCall", () -> instance.ignoredMethod("arg-ignored"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
