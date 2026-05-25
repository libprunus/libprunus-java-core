package contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypeLevelDoNotLogSuperclassSuppressInheritorSubject extends TypeLevelDoNotLogSuperclassSuppressAnchor {

    public String describe(String input) {
        return input;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            TypeLevelDoNotLogSuperclassSuppressInheritorSubject instance =
                    new TypeLevelDoNotLogSuperclassSuppressInheritorSubject();
            CallsiteCapture.step(boundary, "describeCall", () -> instance.describe("arg-input"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
