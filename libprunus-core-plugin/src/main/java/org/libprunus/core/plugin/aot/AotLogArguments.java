package org.libprunus.core.plugin.aot;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record AotLogArguments(
        boolean enabled,
        List<String> targetClassSuffixes,
        List<String> pojoSuffixes,
        String classNameFormat,
        String enterLogLevel,
        String exitLogLevel,
        boolean handleInaccessibleField,
        int maxToStringDepth,
        List<String> toStringWhitelist)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public AotLogArguments {
        targetClassSuffixes = List.copyOf(targetClassSuffixes);
        pojoSuffixes = List.copyOf(pojoSuffixes);
        toStringWhitelist = List.copyOf(toStringWhitelist);
    }
}
