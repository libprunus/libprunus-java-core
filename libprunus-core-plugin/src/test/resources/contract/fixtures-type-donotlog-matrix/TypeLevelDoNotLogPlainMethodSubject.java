package contract;

import org.libprunus.core.log.annotation.DoNotLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DoNotLog
public class TypeLevelDoNotLogPlainMethodSubject {

    public String process(String input) {
        return input;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            TypeLevelDoNotLogPlainMethodSubject instance = new TypeLevelDoNotLogPlainMethodSubject();
            CallsiteCapture.step(boundary, "processCall", () -> instance.process("arg-process"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
