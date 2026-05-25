package contract;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MaxMessageLength;
import org.libprunus.core.log.annotation.MethodLoggingField;
import org.libprunus.core.log.annotation.MethodLoggingProfile;
import org.libprunus.core.log.annotation.ToStringProfile;

@LogRegistry
@MaxMessageLength(4096)
@ToStringProfile(
        includePackages = "contract",
        includeClassSuffixes = {"Dto"})
@MethodLoggingProfile(
        includePackages = "contract",
        includeClassSuffixes = {"StringValueService"},
        fields = {"traceId"})
@MethodLoggingProfile(
        includePackages = "contract",
        includeClassSuffixes = {"NullValueService"},
        fields = {"nullField"})
@MethodLoggingProfile(
        includePackages = "contract",
        includeClassSuffixes = {"PrimitiveValueService"},
        fields = {"counter"})
@MethodLoggingProfile(
        includePackages = "contract",
        includeClassSuffixes = {"ThrowingService"},
        fields = {"throwingField"})
public class LogContextRegistry {

    @MethodLoggingField("traceId")
    public static String traceId() {
        return "trace-xxx";
    }

    @MethodLoggingField("nullField")
    public static String nullField() {
        return null;
    }

    @MethodLoggingField("counter")
    public static int counter() {
        return 42;
    }

    @MethodLoggingField("throwingField")
    public static String throwingField() {
        throw new RuntimeException("extractor-bang");
    }
}
