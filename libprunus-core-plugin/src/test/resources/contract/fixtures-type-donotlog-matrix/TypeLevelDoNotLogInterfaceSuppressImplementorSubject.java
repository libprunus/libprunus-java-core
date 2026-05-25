package contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypeLevelDoNotLogInterfaceSuppressImplementorSubject implements TypeLevelDoNotLogInterfaceSuppressAnchor {

    public String describe(String input) {
        return input;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            TypeLevelDoNotLogInterfaceSuppressImplementorSubject instance =
                    new TypeLevelDoNotLogInterfaceSuppressImplementorSubject();
            CallsiteCapture.step(boundary, "describeCall", () -> instance.describe("arg-input"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
