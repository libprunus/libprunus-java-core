package contract;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutomatedProcessingIgnore
public class AutomatedTypeIgnoreSensitiveParamSubject {

    @DoNotLog
    public String process(@Sensitive String secret, String plain) {
        return secret + "/" + plain;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            AutomatedTypeIgnoreSensitiveParamSubject instance = new AutomatedTypeIgnoreSensitiveParamSubject();
            CallsiteCapture.step(boundary, "processCall", () -> instance.process("secret-val", "plain-val"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
