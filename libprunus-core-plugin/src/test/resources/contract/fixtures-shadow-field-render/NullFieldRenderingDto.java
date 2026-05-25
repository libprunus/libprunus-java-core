package contract;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public class NullFieldRenderingDto {

    @DoLog
    public String a = null;

    @Sensitive
    public String b = null;

    @DoNotLog
    public String c = null;

    public String d = "x";
}
