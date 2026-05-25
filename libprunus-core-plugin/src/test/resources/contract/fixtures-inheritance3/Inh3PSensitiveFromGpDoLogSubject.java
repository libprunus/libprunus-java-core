package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

@Sensitive
public class Inh3PSensitiveFromGpDoLogSubject extends Inh3GpDoLogSubject {

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

    public String pOwn(String x, @Sensitive String s, @DoNotLog String d, @DoLog String l) {
        return x;
    }

    @Override
    public String inheritedMethod(String x, @Sensitive String s, @DoNotLog String d, @DoLog String l) {
        return super.inheritedMethod(x, s, d, l);
    }
}
