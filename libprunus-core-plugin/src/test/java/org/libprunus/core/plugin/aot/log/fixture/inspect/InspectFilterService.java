package org.libprunus.core.plugin.aot.log.fixture.inspect;

import org.libprunus.core.annotation.AutomatedProcessingIgnore;

public class InspectFilterService {

    public InspectFilterService() {}

    public String publicAction(String input) {
        return "public:" + input;
    }

    @SuppressWarnings("unused")
    private String privateAction(String input) {
        return "private:" + input;
    }

    protected String protectedAction(String input) {
        return "protected:" + input;
    }

    String packageAction(String input) {
        return "package:" + input;
    }

    public static String staticAction(String input) {
        return "static:" + input;
    }

    @AutomatedProcessingIgnore
    public String ignoredAction(String input) {
        return "ignored:" + input;
    }

    @Override
    public String toString() {
        return "InspectFilterService";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof InspectFilterService;
    }

    @Override
    public int hashCode() {
        return 42;
    }
}
