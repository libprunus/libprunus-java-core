package contract;

import org.libprunus.core.log.annotation.DoNotLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DoNotLog
public class TypeLevelDoNotLogAncestorDoLogSuppressedBySubclassSubject extends TypeLevelDoNotLogAncestorDoLogAnchor {

    public String describe(String input) {
        return input;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            TypeLevelDoNotLogAncestorDoLogSuppressedBySubclassSubject instance =
                    new TypeLevelDoNotLogAncestorDoLogSuppressedBySubclassSubject();
            CallsiteCapture.step(boundary, "describeCall", () -> instance.describe("arg-input"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
