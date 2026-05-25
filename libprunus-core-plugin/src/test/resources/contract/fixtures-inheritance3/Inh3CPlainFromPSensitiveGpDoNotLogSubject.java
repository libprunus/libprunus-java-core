package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Inh3CPlainFromPSensitiveGpDoNotLogSubject extends Inh3PSensitiveFromGpDoNotLogSubject {

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

    public String cOwn(String x, @Sensitive String s, @DoNotLog String d, @DoLog String l) {
        return x;
    }

    @Override
    public String inheritedMethod(String x, @Sensitive String s, @DoNotLog String d, @DoLog String l) {
        return super.inheritedMethod(x, s, d, l);
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            Inh3CPlainFromPSensitiveGpDoNotLogSubject inst = new Inh3CPlainFromPSensitiveGpDoNotLogSubject();
            CallsiteCapture.step(boundary, "gpOwn", () -> inst.gpOwn("arg-x", "arg-s", "arg-d", "arg-l"));
            CallsiteCapture.step(boundary, "pOwn", () -> inst.pOwn("arg-x", "arg-s", "arg-d", "arg-l"));
            CallsiteCapture.step(boundary, "cOwn", () -> inst.cOwn("arg-x", "arg-s", "arg-d", "arg-l"));
            CallsiteCapture.step(
                    boundary, "inheritedMethod", () -> inst.inheritedMethod("arg-x", "arg-s", "arg-d", "arg-l"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
