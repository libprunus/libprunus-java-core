package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedClassSensitiveCallsiteAccessMatrixService extends ClassSensitiveCallsiteAccessMatrixService {

    public String subPublicInstance(String x, @Sensitive String s, @DoNotLog String d, @DoLog String l) {
        return x;
    }

    @Sensitive
    public String subPublicInstanceSensitive(String x, @Sensitive String s, @DoNotLog String d, @DoLog String l) {
        return x;
    }

    @DoNotLog
    public String subPublicInstanceDoNotLog(String x, @Sensitive String s, @DoNotLog String d, @DoLog String l) {
        return x;
    }

    @DoNotLog
    public String subPublicInstanceDoNotLogPure(String x) {
        return x;
    }

    @DoLog
    public String subPublicInstanceDoLog(String x, @Sensitive String s, @DoNotLog String d, @DoLog String l) {
        return x;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            ExtendedClassSensitiveCallsiteAccessMatrixService instance =
                    new ExtendedClassSensitiveCallsiteAccessMatrixService();
            CallsiteCapture.step(
                    boundary,
                    "publicStatic",
                    () -> ClassSensitiveCallsiteAccessMatrixService.publicStatic("arg-publicStatic"));
            CallsiteCapture.step(
                    boundary,
                    "protectedStatic",
                    () -> ClassSensitiveCallsiteAccessMatrixService.protectedStatic("arg-protectedStatic"));
            CallsiteCapture.step(
                    boundary,
                    "publicInstance",
                    () -> instance.publicInstance(
                            "arg-publicInstance-x",
                            "arg-publicInstance-s",
                            "arg-publicInstance-d",
                            "arg-publicInstance-l"));
            CallsiteCapture.step(
                    boundary,
                    "publicInstanceSensitive",
                    () -> instance.publicInstanceSensitive(
                            "arg-publicInstanceSensitive-x",
                            "arg-publicInstanceSensitive-s",
                            "arg-publicInstanceSensitive-d",
                            "arg-publicInstanceSensitive-l"));
            CallsiteCapture.step(
                    boundary,
                    "publicInstanceDoNotLog",
                    () -> instance.publicInstanceDoNotLog(
                            "arg-publicInstanceDoNotLog-x",
                            "arg-publicInstanceDoNotLog-s",
                            "arg-publicInstanceDoNotLog-d",
                            "arg-publicInstanceDoNotLog-l"));
            CallsiteCapture.step(
                    boundary,
                    "publicInstanceDoNotLogPure",
                    () -> instance.publicInstanceDoNotLogPure("arg-publicInstanceDoNotLogPure-x"));
            CallsiteCapture.step(
                    boundary,
                    "publicInstanceDoLog",
                    () -> instance.publicInstanceDoLog(
                            "arg-publicInstanceDoLog-x",
                            "arg-publicInstanceDoLog-s",
                            "arg-publicInstanceDoLog-d",
                            "arg-publicInstanceDoLog-l"));
            CallsiteCapture.step(
                    boundary, "protectedInstance", () -> instance.protectedInstance("arg-protectedInstance"));
            CallsiteCapture.step(
                    boundary,
                    "subPublicInstance",
                    () -> instance.subPublicInstance(
                            "arg-subPublicInstance-x",
                            "arg-subPublicInstance-s",
                            "arg-subPublicInstance-d",
                            "arg-subPublicInstance-l"));
            CallsiteCapture.step(
                    boundary,
                    "subPublicInstanceSensitive",
                    () -> instance.subPublicInstanceSensitive(
                            "arg-subPublicInstanceSensitive-x",
                            "arg-subPublicInstanceSensitive-s",
                            "arg-subPublicInstanceSensitive-d",
                            "arg-subPublicInstanceSensitive-l"));
            CallsiteCapture.step(
                    boundary,
                    "subPublicInstanceDoNotLog",
                    () -> instance.subPublicInstanceDoNotLog(
                            "arg-subPublicInstanceDoNotLog-x",
                            "arg-subPublicInstanceDoNotLog-s",
                            "arg-subPublicInstanceDoNotLog-d",
                            "arg-subPublicInstanceDoNotLog-l"));
            CallsiteCapture.step(
                    boundary,
                    "subPublicInstanceDoNotLogPure",
                    () -> instance.subPublicInstanceDoNotLogPure("arg-subPublicInstanceDoNotLogPure-x"));
            CallsiteCapture.step(
                    boundary,
                    "subPublicInstanceDoLog",
                    () -> instance.subPublicInstanceDoLog(
                            "arg-subPublicInstanceDoLog-x",
                            "arg-subPublicInstanceDoLog-s",
                            "arg-subPublicInstanceDoLog-d",
                            "arg-subPublicInstanceDoLog-l"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
