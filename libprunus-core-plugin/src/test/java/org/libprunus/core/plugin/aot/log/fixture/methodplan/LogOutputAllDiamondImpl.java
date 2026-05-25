package org.libprunus.core.plugin.aot.log.fixture.methodplan;

public class LogOutputAllDiamondImpl implements LogOutputAllDiamondLeft, LogOutputDiamondRight {

    @Override
    public String resolve(String input) {
        return input;
    }
}
