package contract;

import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public class MultiFamilyOnFieldDto {
    @Sensitive
    @DoNotLog
    public String collidingField = "collide";
}
