package org.libprunus.core.plugin.aot.log.fixture.typenodebuilder;

@SuppressWarnings("unused")
public class ObjectOverrider {

    @Override
    public String toString() {
        return "x";
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        return false;
    }
}
