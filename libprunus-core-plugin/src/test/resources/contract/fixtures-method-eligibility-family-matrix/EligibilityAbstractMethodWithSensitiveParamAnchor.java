package contract;

import org.libprunus.core.log.annotation.Sensitive;

public abstract class EligibilityAbstractMethodWithSensitiveParamAnchor {

    public abstract String handle(@Sensitive String secret);
}
