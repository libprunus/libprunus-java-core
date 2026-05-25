package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public class ExtendedClassDoLogAccessAndAnnotationMatrixDto extends ClassDoLogAccessAndAnnotationMatrixDto {

    public String subPublicPlain = "plain-sub-public-value";
    protected String subProtectedPlain = "plain-sub-protected-value";
    String subPackagePlain = "plain-sub-package-value";
    private String subPrivatePlain = "plain-sub-private-value";

    @Sensitive
    public String subPublicSensitive = "sensitive-sub-public-value";

    @Sensitive
    protected String subProtectedSensitive = "sensitive-sub-protected-value";

    @Sensitive
    String subPackageSensitive = "sensitive-sub-package-value";

    @Sensitive
    private String subPrivateSensitive = "sensitive-sub-private-value";

    @DoNotLog
    public String subPublicDoNotLog = "donotlog-sub-public-value";

    @DoNotLog
    protected String subProtectedDoNotLog = "donotlog-sub-protected-value";

    @DoNotLog
    String subPackageDoNotLog = "donotlog-sub-package-value";

    @DoNotLog
    private String subPrivateDoNotLog = "donotlog-sub-private-value";

    @DoLog
    public String subPublicDoLog = "dolog-sub-public-value";

    @DoLog
    protected String subProtectedDoLog = "dolog-sub-protected-value";

    @DoLog
    String subPackageDoLog = "dolog-sub-package-value";

    @DoLog
    private String subPrivateDoLog = "dolog-sub-private-value";
}
