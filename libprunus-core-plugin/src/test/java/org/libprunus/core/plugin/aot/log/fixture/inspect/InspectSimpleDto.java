package org.libprunus.core.plugin.aot.log.fixture.inspect;

import java.util.List;
import java.util.Map;
import org.libprunus.core.log.annotation.Sensitive;

public class InspectSimpleDto {

    public String name;

    @Sensitive
    public String secret;

    public int count;

    public long timestamp;

    public boolean active;

    public List<String> tags;

    public Map<String, Integer> scores;

    public String[] aliases;
}
