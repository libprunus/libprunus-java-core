package contract;

import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParameterScopeSubject {

    public ParameterScopeSubject() {}

    public ParameterScopeSubject(@Sensitive String secretCtorArg) {}

    public String publicMethod(String s) {
        return s;
    }

    String packageMethod(@Sensitive String secret) {
        return secret;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            CallsiteCapture.step(boundary, "ctor", () -> new ParameterScopeSubject("arg-ctor-secret"));
            ParameterScopeSubject inst = new ParameterScopeSubject();
            CallsiteCapture.step(boundary, "publicMethod", () -> inst.publicMethod("arg-public-s"));
            CallsiteCapture.step(boundary, "packageMethod", () -> inst.packageMethod("arg-pkg-s"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
