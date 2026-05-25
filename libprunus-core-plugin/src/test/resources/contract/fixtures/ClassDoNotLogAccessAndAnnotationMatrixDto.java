package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

@DoNotLog
public class ClassDoNotLogAccessAndAnnotationMatrixDto {

    public static String publicStatic = "static-public-value";
    protected static String protectedStatic = "static-protected-value";
    static String packageStatic = "static-package-value";
    private static String privateStatic = "static-private-value";

    public String publicPlain = "plain-public-value";
    protected String protectedPlain = "plain-protected-value";
    String packagePlain = "plain-package-value";
    private String privatePlain = "plain-private-value";

    @Sensitive
    public String publicSensitive = "sensitive-public-value";

    @Sensitive
    protected String protectedSensitive = "sensitive-protected-value";

    @Sensitive
    String packageSensitive = "sensitive-package-value";

    @Sensitive
    private String privateSensitive = "sensitive-private-value";

    @DoNotLog
    public String publicDoNotLog = "donotlog-public-value";

    @DoNotLog
    protected String protectedDoNotLog = "donotlog-protected-value";

    @DoNotLog
    String packageDoNotLog = "donotlog-package-value";

    @DoNotLog
    private String privateDoNotLog = "donotlog-private-value";

    @DoLog
    public String publicDoLog = "dolog-public-value";

    @DoLog
    protected String protectedDoLog = "dolog-protected-value";

    @DoLog
    String packageDoLog = "dolog-package-value";

    @DoLog
    private String privateDoLog = "dolog-private-value";
}
