package contract;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;

@AutomatedProcessingIgnore
public class AutomatedProcessingIgnoreNotInheritedParent {

    public String parentField = "parent-val";

    public String parentMethod(String x) {
        return x + "-parent-result";
    }
}
