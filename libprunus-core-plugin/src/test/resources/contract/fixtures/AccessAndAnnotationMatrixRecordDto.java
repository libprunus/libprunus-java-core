package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public record AccessAndAnnotationMatrixRecordDto(
        String privatePlain,
        @Sensitive String privateSensitive,
        @DoNotLog String privateDoNotLog,
        @DoLog String privateDoLog) {

    public static String publicStatic = "static-public-record-value";
    protected static String protectedStatic = "static-protected-record-value";
    static String packageStatic = "static-package-record-value";
    private static String privateStatic = "static-private-record-value";

    public AccessAndAnnotationMatrixRecordDto() {
        this(
                "plain-private-record-value",
                "sensitive-private-record-value",
                "donotlog-private-record-value",
                "dolog-private-record-value");
    }
}
