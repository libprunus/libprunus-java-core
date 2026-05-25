package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public class InspectMaskedService {

    public String lookup(@DoNotLog String id, String fallback) {
        if (id != null) {
            return id;
        }
        return fallback;
    }

    @Sensitive
    public String transfer(String from, String to) {
        if (from == null || to == null) {
            return "(none)";
        }
        return from + "->" + to;
    }

    public String rank(int score, String label) {
        if (score <= 0) {
            return "invalid";
        }
        return label + "-" + score;
    }

    @DoNotLog
    public String describe(@Sensitive String id) {
        return "id=" + id;
    }
}
