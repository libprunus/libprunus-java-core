package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DoNotLog
public class TypeLevelDoNotLogMixedParamsSubject {

    public String describe(String plain, @Sensitive String masked, @DoLog String visible, @DoNotLog String suppressed) {
        return plain;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            TypeLevelDoNotLogMixedParamsSubject instance = new TypeLevelDoNotLogMixedParamsSubject();
            CallsiteCapture.step(
                    boundary,
                    "describeCall",
                    () -> instance.describe("arg-plain", "arg-masked", "arg-visible", "arg-suppressed"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
