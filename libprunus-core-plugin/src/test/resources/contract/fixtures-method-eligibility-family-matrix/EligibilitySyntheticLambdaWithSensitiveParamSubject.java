package contract;

import java.util.function.Function;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilitySyntheticLambdaWithSensitiveParamSubject {

    public final Function<String, String> lambda = (@Sensitive String secret) -> secret + "-lambda";

    public String driveLambda(String value) {
        return lambda.apply(value);
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            EligibilitySyntheticLambdaWithSensitiveParamSubject instance =
                    new EligibilitySyntheticLambdaWithSensitiveParamSubject();
            CallsiteCapture.step(boundary, "driveLambdaCall", () -> instance.driveLambda("arg-secret"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
