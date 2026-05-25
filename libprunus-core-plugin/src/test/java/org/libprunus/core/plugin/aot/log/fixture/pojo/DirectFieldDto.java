package org.libprunus.core.plugin.aot.log.fixture.pojo;

public class DirectFieldDto {

    public String value = "field-value";

    public String getValue() {
        throw new IllegalStateException("getter must not be called");
    }
}
