package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DoNotLog
public class TypeLevelDoNotLogMethodDoLogParamDoNotLogSubject {

    @DoLog
    public String describe(String visible, @DoNotLog String dropped) {
        return visible;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            TypeLevelDoNotLogMethodDoLogParamDoNotLogSubject instance =
                    new TypeLevelDoNotLogMethodDoLogParamDoNotLogSubject();
            CallsiteCapture.step(boundary, "describeCall", () -> instance.describe("arg-visible", "arg-dropped"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
