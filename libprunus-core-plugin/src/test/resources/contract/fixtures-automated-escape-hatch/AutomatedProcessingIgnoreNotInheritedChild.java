package contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutomatedProcessingIgnoreNotInheritedChild extends AutomatedProcessingIgnoreNotInheritedParent {

    public String childField = "child-val";

    public String childMethod(String x) {
        return x + "-child-result";
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            AutomatedProcessingIgnoreNotInheritedChild instance = new AutomatedProcessingIgnoreNotInheritedChild();
            CallsiteCapture.step(boundary, "childMethodCall", () -> instance.childMethod("arg-child"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
