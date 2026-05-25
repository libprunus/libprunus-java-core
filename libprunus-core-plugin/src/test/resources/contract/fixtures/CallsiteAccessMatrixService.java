package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallsiteAccessMatrixService {

    public static String publicStatic(String x) {
        return x;
    }

    protected static String protectedStatic(String x) {
        return x;
    }

    static String packageStatic(String x) {
        return x;
    }

    private static String privateStatic(String x) {
        return x;
    }

    public String publicInstance(String x, @Sensitive String s, @DoNotLog String d, @DoLog String l) {
        return x;
    }

    @Sensitive
    public String publicInstanceSensitive(String x, @Sensitive String s, @DoNotLog String d, @DoLog String l) {
        return x;
    }

    @DoNotLog
    public String publicInstanceDoNotLog(String x, @Sensitive String s, @DoNotLog String d, @DoLog String l) {
        return x;
    }

    @DoNotLog
    public String publicInstanceDoNotLogPure(String x) {
        return x;
    }

    @DoLog
    public String publicInstanceDoLog(String x, @Sensitive String s, @DoNotLog String d, @DoLog String l) {
        return x;
    }

    protected String protectedInstance(String x) {
        return x;
    }

    String packageInstance(String x) {
        return x;
    }

    private String privateInstance(String x) {
        return x;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            CallsiteAccessMatrixService instance = new CallsiteAccessMatrixService();
            CallsiteCapture.step(boundary, "publicStatic", () -> publicStatic("arg-publicStatic"));
            CallsiteCapture.step(boundary, "protectedStatic", () -> protectedStatic("arg-protectedStatic"));
            CallsiteCapture.step(boundary, "packageStatic", () -> packageStatic("arg-packageStatic"));
            CallsiteCapture.step(boundary, "privateStatic", () -> privateStatic("arg-privateStatic"));
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
            CallsiteCapture.step(boundary, "packageInstance", () -> instance.packageInstance("arg-packageInstance"));
            CallsiteCapture.step(boundary, "privateInstance", () -> instance.privateInstance("arg-privateInstance"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
