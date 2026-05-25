package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.Sensitive;

public class MultiFamilyOnFieldDto {
    @Sensitive
    @DoLog
    public String collidingField = "collide";
}
