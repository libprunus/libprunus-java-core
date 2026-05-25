package contract;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MaxMessageLength;
import org.libprunus.core.log.annotation.MethodLoggingField;
import org.libprunus.core.log.annotation.MethodLoggingProfile;

@LogRegistry
@MaxMessageLength(4096)
@MethodLoggingProfile(
        includePackages = "contract",
        includeClassSuffixes = {"UnreferencedFieldService"},
        fields = {"traceId"})
public class LogContextRegistry {

    @MethodLoggingField("traceId")
    public static String traceId() {
        return "trace-xxx";
    }

    @MethodLoggingField("unused")
    public static String unused() {
        return "unused-value";
    }
}
