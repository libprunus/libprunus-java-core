package org.libprunus.core.plugin.aot.task

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import org.libprunus.core.log.runtime.CallsiteBindingProtocol
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import spock.lang.Specification
import spock.lang.TempDir

class GenerateAotBindingTaskResourceWriteIntegrationSpec extends Specification {

    @TempDir
    Path outputDir

    def "writeAotResourceFiles creates SPI and callsite pointer files with newline-terminated content matching inputs"() {
        given:
        def callsiteClass = bindingClass + "Callsite"
        Path spiFile = outputDir.resolve(PrunusPluginConstants.SPI_SERVICES_DIR)
                .resolve(PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN)
        Path callsitePointer = outputDir.resolve(PrunusPluginConstants.AOT_RUNTIME_CALLSITE_DIR)
                .resolve(CallsiteBindingProtocol.RESOURCE_FILENAME)

        when:
        GenerateAotBindingTask.writeAotResourceFiles(outputDir, bindingClass, callsiteClass)

        then:
        Files.exists(spiFile)
        Files.exists(callsitePointer)

        and:
        def spiText = Files.readString(spiFile)
        def callsiteText = Files.readString(callsitePointer)
        spiText.endsWith("\n")
        callsiteText.endsWith("\n")
        spiText.trim() == bindingClass
        callsiteText.trim() == callsiteClass

        where:
        bindingClass << ["com.example.Binding", "com.example.deeply.nested.Binding"]
    }

    def "writeAotResourceFiles re-invoked with identical inputs produces byte-identical SPI and callsite pointer content"() {
        given:
        def callsiteClass = bindingClass + "Callsite"
        Path spiFile = outputDir.resolve(PrunusPluginConstants.SPI_SERVICES_DIR)
                .resolve(PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN)
        Path callsitePointer = outputDir.resolve(PrunusPluginConstants.AOT_RUNTIME_CALLSITE_DIR)
                .resolve(CallsiteBindingProtocol.RESOURCE_FILENAME)

        when:
        GenerateAotBindingTask.writeAotResourceFiles(outputDir, bindingClass, callsiteClass)
        def firstSpi = Files.readString(spiFile)
        def firstCallsite = Files.readString(callsitePointer)
        GenerateAotBindingTask.writeAotResourceFiles(outputDir, bindingClass, callsiteClass)
        def secondSpi = Files.readString(spiFile)
        def secondCallsite = Files.readString(callsitePointer)

        then:
        firstSpi == secondSpi
        firstCallsite == secondCallsite

        where:
        bindingClass << ["com.example.Binding", "com.example.deeply.nested.Binding"]
    }

    def "writeAotResourceFiles preserves mtime of SPI and callsite files when re-invoked with identical inputs"() {
        given:
        def bindingClass = "com.example.Binding"
        def callsiteClass = "com.example.runtime.Callsite"
        GenerateAotBindingTask.writeAotResourceFiles(outputDir, bindingClass, callsiteClass)

        Path spiFile = outputDir.resolve(PrunusPluginConstants.SPI_SERVICES_DIR)
                .resolve(PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN)
        Path callsitePointer = outputDir.resolve(PrunusPluginConstants.AOT_RUNTIME_CALLSITE_DIR)
                .resolve(CallsiteBindingProtocol.RESOURCE_FILENAME)
        def pastMtime = FileTime.fromMillis(0L)
        Files.setLastModifiedTime(spiFile, pastMtime)
        Files.setLastModifiedTime(callsitePointer, pastMtime)

        when:
        GenerateAotBindingTask.writeAotResourceFiles(outputDir, bindingClass, callsiteClass)

        then:
        Files.getLastModifiedTime(spiFile) == pastMtime
        Files.getLastModifiedTime(callsitePointer) == pastMtime

        and:
        Files.readString(spiFile).trim() == bindingClass
        Files.readString(callsitePointer).trim() == callsiteClass
    }

    def "writeAotResourceFiles bumps SPI mtime when binding class changes while preserving callsite pointer mtime"() {
        given:
        def callsiteClass = "com.example.runtime.Callsite"
        GenerateAotBindingTask.writeAotResourceFiles(outputDir, "com.example.OldBinding", callsiteClass)

        Path spiFile = outputDir.resolve(PrunusPluginConstants.SPI_SERVICES_DIR)
                .resolve(PrunusPluginConstants.ABSTRACT_LOG_CONFIG_FQCN)
        Path callsitePointer = outputDir.resolve(PrunusPluginConstants.AOT_RUNTIME_CALLSITE_DIR)
                .resolve(CallsiteBindingProtocol.RESOURCE_FILENAME)
        def pastMtime = FileTime.fromMillis(0L)
        Files.setLastModifiedTime(spiFile, pastMtime)
        Files.setLastModifiedTime(callsitePointer, pastMtime)

        when:
        GenerateAotBindingTask.writeAotResourceFiles(outputDir, "com.example.NewBinding", callsiteClass)

        then:
        Files.getLastModifiedTime(spiFile) != pastMtime
        Files.readString(spiFile).trim() == "com.example.NewBinding"

        and:
        Files.getLastModifiedTime(callsitePointer) == pastMtime
        Files.readString(callsitePointer).trim() == callsiteClass
    }

    def "writeAotResourceFiles leaves no tmp residue under SPI or callsite directories"() {
        given:
        def bindingClass = "com.example.Binding"
        def callsiteClass = "com.example.runtime.Callsite"

        when:
        GenerateAotBindingTask.writeAotResourceFiles(outputDir, bindingClass, callsiteClass)

        then:
        listTmpFiles(outputDir.resolve(PrunusPluginConstants.SPI_SERVICES_DIR)).isEmpty()
        listTmpFiles(outputDir.resolve(PrunusPluginConstants.AOT_RUNTIME_CALLSITE_DIR)).isEmpty()
    }

    private static List<Path> listTmpFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return []
        }
        Files.list(dir).withCloseable { stream ->
            stream.collect().findAll { it.fileName.toString().endsWith(".tmp") }
        }
    }
}
