package contract;

import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MethodEligibleShapesService {

    private final int seed;

    public MethodEligibleShapesService(int seed) {
        this.seed = seed;
    }

    public static String publicStaticMethod(String x) {
        return x;
    }

    public String publicInstance(String x) {
        return x + "-result";
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

    @Override
    public boolean equals(Object other) {
        return other instanceof MethodEligibleShapesService && ((MethodEligibleShapesService) other).seed == this.seed;
    }

    @Override
    public int hashCode() {
        return seed;
    }

    @Override
    public String toString() {
        return "MethodEligibleShapesService(seed=" + seed + ")";
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            MethodEligibleShapesService instance = new MethodEligibleShapesService(7);

            CallsiteCapture.step(boundary, "publicStaticCall", () -> publicStaticMethod("arg-publicStatic"));
            CallsiteCapture.step(boundary, "constructorCall", () -> new MethodEligibleShapesService(11));
            CallsiteCapture.step(boundary, "publicInstanceCall", () -> instance.publicInstance("arg-publicInstance"));
            CallsiteCapture.step(
                    boundary, "protectedInstanceCall", () -> instance.protectedInstance("arg-protectedInstance"));
            CallsiteCapture.step(
                    boundary, "packageInstanceCall", () -> instance.packageInstance("arg-packageInstance"));
            CallsiteCapture.step(
                    boundary, "privateInstanceCall", () -> instance.privateInstance("arg-privateInstance"));

            Function<String, String> lambda = (s) -> s + "-lambda-result";
            CallsiteCapture.step(boundary, "syntheticLambdaCall", () -> lambda.apply("arg-lambda"));

            CallsiteCapture.step(boundary, "equalsCall", () -> instance.equals(new MethodEligibleShapesService(7)));
            CallsiteCapture.step(boundary, "hashCodeCall", () -> instance.hashCode());
            CallsiteCapture.step(boundary, "toStringCall", () -> instance.toString());

            boundary.info("===BOUNDARY END===");
        });
    }
}
