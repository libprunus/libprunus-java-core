package contract;

import org.libprunus.core.log.annotation.Sensitive;

@Sensitive
public interface SensitiveInterface {
    String act(String x, String s);
}
