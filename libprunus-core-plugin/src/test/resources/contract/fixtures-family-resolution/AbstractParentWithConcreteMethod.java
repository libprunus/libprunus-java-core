package contract;

import org.libprunus.core.log.annotation.Sensitive;

public abstract class AbstractParentWithConcreteMethod {
    public String compute(String x, @Sensitive String s) {
        return x;
    }
}
