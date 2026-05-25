package org.libprunus.core.plugin.aot.log.fixture.methodplan;

public class LogOutputIgnoreAllDiamondImpl implements LogOutputIgnoreDiamondLeft, LogOutputMaskedDiamondRight {

    @Override
    public String resolve(String input) {
        return input;
    }
}
