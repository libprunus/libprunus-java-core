package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;

public class MultiFamilyOnFieldDto {
    @DoLog
    @DoNotLog
    public String collidingField = "collide";
}
