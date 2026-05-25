package contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractParentInheritingSubject extends AbstractParentWithConcreteMethod {

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            AbstractParentInheritingSubject inst = new AbstractParentInheritingSubject();
            CallsiteCapture.step(boundary, "compute", () -> inst.compute("arg-x", "arg-s"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
