package org.libprunus.core.plugin.aot.log.contract

final class ContractResultReader {

    static String readToString(File projectDir, String simpleClassName) {
        def file = new File(projectDir, "build/contract-results/${simpleClassName}.tostring.txt")
        assert file.isFile(), "toString result not produced for ${simpleClassName}"
        file.getText('UTF-8')
    }

    static String readError(File projectDir, String simpleClassName) {
        def file = new File(projectDir, "build/contract-results/${simpleClassName}.error.txt")
        assert file.isFile(), "error result not produced for ${simpleClassName}"
        file.getText('UTF-8')
    }

    static boolean readLoggable(File projectDir, String simpleClassName) {
        def file = new File(projectDir, "build/contract-results/${simpleClassName}.loggable.txt")
        assert file.isFile(), "loggable result not produced for ${simpleClassName}"
        Boolean.parseBoolean(file.getText('UTF-8').trim())
    }

    static String readCallsite(File projectDir, String simpleClassName) {
        def file = new File(projectDir, "build/contract-results/${simpleClassName}.callsite.txt")
        assert file.isFile(), "callsite result not produced for ${simpleClassName}"
        file.getText('UTF-8')
    }
}
