package contract;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;
import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

@AutomatedProcessingIgnore
public class AutomatedTypeIgnoreSensitiveFieldsSubject {

    @Sensitive
    public String maskedField = "masked-val";

    @DoNotLog
    public String suppressedField = "suppressed-val";

    @DoLog
    public String passThroughField = "passthrough-val";

    public String plainField = "plain-val";
}
