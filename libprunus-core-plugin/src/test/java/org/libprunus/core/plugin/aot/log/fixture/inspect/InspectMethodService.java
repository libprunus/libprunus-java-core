package org.libprunus.core.plugin.aot.log.fixture.inspect;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.libprunus.core.log.annotation.Sensitive;

public class InspectMethodService {

    public String resolveUser(String username, @Sensitive String token) {
        if (username == null) {
            return "anonymous";
        }
        return "user-" + username;
    }

    public int computeScore(int base, int multiplier) {
        return base * multiplier;
    }

    public boolean authorize(String role, @Sensitive String credential) {
        return role != null && !role.isEmpty();
    }

    public List<String> listItems(Collection<String> filter, int limit) {
        return List.of();
    }

    public void processEvent(String eventType, Map<String, String> payload) {}

    public String classify(int n) {
        if (n > 0) {
            return "positive";
        }
        if (n < 0) {
            return "negative";
        }
        return "zero";
    }
}
