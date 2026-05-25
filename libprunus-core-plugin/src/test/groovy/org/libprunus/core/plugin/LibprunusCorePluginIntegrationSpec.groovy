package org.libprunus.core.plugin

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import org.gradle.testkit.runner.GradleRunner
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import spock.lang.Specification
import spock.lang.TempDir

class LibprunusCorePluginIntegrationSpec extends Specification {

    @TempDir
    File testProjectDir

    @TempDir
    File anotherTestProjectDir

    def "verifyAotInstrumentation reuses configuration cache across consecutive real Gradle build runs"() {
        given:
        def repoRoot = findRepoRoot()
        writeSampleProject(testProjectDir, repoRoot)

        when:
        def firstResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('verifyAotInstrumentation', '--configuration-cache')
                .build()
        def secondResult = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments('verifyAotInstrumentation', '--configuration-cache')
            .build()

        then:
        firstResult.output.contains('BUILD SUCCESSFUL')
        firstResult.output.contains(':verifyAotInstrumentation')
        firstResult.output.contains('Configuration cache entry stored')
        secondResult.output.contains('BUILD SUCCESSFUL')
        secondResult.output.contains(':verifyAotInstrumentation')
        secondResult.output.contains('Reusing configuration cache')
    }

    def "plugin weaves compiled classes in different project paths"() {
        given:
        def repoRoot = findRepoRoot()
        writeSampleProject(testProjectDir, repoRoot)
        writeSampleProject(anotherTestProjectDir, repoRoot)

        when:
        def firstResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('verifyAotInstrumentation')
                .build()

        and:
        def secondResult = GradleRunner.create()
                .withProjectDir(anotherTestProjectDir)
                .withPluginClasspath()
                .withArguments('verifyAotInstrumentation')
                .build()

        then:
        firstResult.output.contains('BUILD SUCCESSFUL')
        secondResult.output.contains('BUILD SUCCESSFUL')
        firstResult.output.readLines().any { it.contains(':byteBuddy') }
        secondResult.output.readLines().any { it.contains(':byteBuddy') }
    }

    def "byteBuddy task registers classes output directory as project-relative forward-slash path"() {
        given:
        def repoRoot = findRepoRoot()
        writeSampleProjectWithPropertyInspectionTask(testProjectDir, repoRoot)

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('printByteBuddyClassesOutputDir')
                .build()

        then:
        def propLine = result.output.readLines().find { it.startsWith('CLASSES_DIR_PROP=') }
        propLine != null
        def propValue = propLine.substring('CLASSES_DIR_PROP='.length())
        !propValue.startsWith('/')
        !propValue.startsWith(testProjectDir.absolutePath)
        propValue.contains('classes')
        !propValue.contains('\\')
    }

    private static File findRepoRoot() {
        File current = new File(System.getProperty('user.dir')).canonicalFile
        while (current != null && !new File(current, 'settings.gradle.kts').exists()) {
            current = current.parentFile
        }
        assert current != null
        current
    }

    def "plugin fails fast when toStringWhitelist contains missing type"() {
        given:
        def repoRoot = findRepoRoot()
        writeSampleProject(testProjectDir, repoRoot, ["sample.MissingWhitelistType"])

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('classes')
                .buildAndFail()

        then:
        !result.output.contains('BUILD SUCCESSFUL')
        result.output.contains('cannot find symbol')
        result.output.contains('MissingWhitelistType')
        def generatedBindingRoot = new File(testProjectDir, 'build/generated/classes/aot-binding/main/org/libprunus/aot/generated')
        !generatedBindingRoot.exists() || generatedBindingRoot.listFiles().length == 0
    }

    def "classes output contains binding impl and runtime callsite class files"() {
        given:
        def repoRoot = findRepoRoot()
        writeSampleProject(testProjectDir, repoRoot)

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('classes')
                .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
        def generatedRoot = new File(testProjectDir, 'build/generated/classes/aot-binding/main/org/libprunus/aot/generated')
        generatedRoot.isDirectory()
        def generatedFiles = []
        generatedRoot.eachFileRecurse { file ->
            generatedFiles << file
        }
        generatedFiles.any { it.name == 'LogConfigBindingImpl.class' }
        generatedFiles.any { it.name == 'RuntimeBindingCallsite.class' }
    }

    def "runtime classpath can invoke generated runtime binding callsite"() {
        given:
        def repoRoot = findRepoRoot()
        writeSampleProject(testProjectDir, repoRoot)

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('verifyAotRuntimeBinding')
                .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
        result.output.contains(':verifyAotRuntimeBinding')
    }

    def "runtime classpath can discover and invoke callsite via pointer file"() {
        given:
        def repoRoot = findRepoRoot()
        writeSampleProject(testProjectDir, repoRoot)

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('verifyAotRuntimeCallsiteDiscovery')
                .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
        result.output.contains(':verifyAotRuntimeCallsiteDiscovery')
    }

    def "resolve task fails when explicit provider binding class property points to missing class"() {
        given:
        def repoRoot = findRepoRoot()
        writeSampleProject(testProjectDir, repoRoot)
        writeExplicitBindingProperty(testProjectDir, 'sample.MissingBinding')

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('resolveLogConfigProviderConflict')
                .buildAndFail()

        then:
        !result.output.contains('BUILD SUCCESSFUL')
        result.output.contains(':resolveLogConfigProviderConflict')
        result.output.contains('Binding class sample.MissingBinding not found in classpath')
        !result.output.contains(':' + PrunusPluginConstants.GENERATE_AOT_BINDING_TASK)
    }

    def "resolve task succeeds when explicit provider binding class property points to existing runtime dependency class"() {
        given:
        def repoRoot = findRepoRoot()
        writeSampleProject(testProjectDir, repoRoot)
        writeRuntimeOnlyDependencyForBindingClass(testProjectDir, 'sample.CustomBinding')
        writeExplicitBindingProperty(testProjectDir, 'sample.CustomBinding')

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('resolveLogConfigProviderConflict')
                .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
        result.output.contains(':resolveLogConfigProviderConflict')
    }

    private static void writeSampleProject(File projectDir, File repoRoot) {
        writeSampleProject(projectDir, repoRoot, [])
    }

    private static void writeExplicitBindingProperty(File projectDir, String bindingClassName) {
        new File(projectDir, 'gradle.properties').text = "prunus.aot.provider.bindingClass=${bindingClassName}\n"
    }

    private static void writeRuntimeOnlyDependencyForBindingClass(File projectDir, String fqcn) {
        int dot = fqcn.lastIndexOf('.')
        String classEntry = (dot > 0 ? fqcn.replace('.', '/') : fqcn) + '.class'
        File libsDir = new File(projectDir, 'libs')
        libsDir.mkdirs()
        File jar = new File(libsDir, 'custom-binding.jar')
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { jos ->
            jos.putNextEntry(new JarEntry(classEntry))
            jos.write(new byte[0])
            jos.closeEntry()
        }
        new File(projectDir, 'build.gradle') << "\ndependencies {\n    runtimeOnly files('libs/custom-binding.jar')\n}\n"
    }

    private static void writeSampleProject(File projectDir, File repoRoot, List<String> toStringWhitelist) {
        def escapedRepoRoot = repoRoot.absolutePath.replace('\\', '\\\\')
        def whitelistAnnotation = toStringWhitelist.isEmpty()
            ? ""
            : "@DirectToStringWhitelist({${toStringWhitelist.collect { "${it}.class" }.join(', ')}})"
        def whitelistVerification = ""
        new File(projectDir, 'settings.gradle').text = """
rootProject.name = 'sample-app'
includeBuild('${escapedRepoRoot}')
""".stripIndent()
        new File(projectDir, 'build.gradle').text = '''
plugins {
    id 'org.libprunus.libprunus-core-plugin'
}

prunus {
    aot {
        enabled = true
        logRegistryClass = 'org.libprunus.aot.LogContextRegistry'
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.libprunus:libprunus-core:0.0.1-SNAPSHOT'
}

def mainClassesDir = layout.buildDirectory.dir('classes/java/main')
def mainRuntimeClasspath = sourceSets.main.runtimeClasspath

tasks.register('verifyAotInstrumentation') {
    dependsOn tasks.named('classes')
    doLast {
        def classesDir = mainClassesDir.get().asFile
        def runtimeUrls = mainRuntimeClasspath.files.collect { it.toURI().toURL() } as URL[]
        def loader = new URLClassLoader(runtimeUrls, ClassLoader.systemClassLoader)
        def loggableType = loader.loadClass('org.libprunus.core.log.runtime.Loggable')
        ['sample.UserDto', 'sample.NestedDto', 'sample.ChildDto', 'sample.LargeDto',
         'sample.PointDto', 'sample.BaseDto', 'sample.MiddleDto', 'sample.DeepDto',
         'sample.FilteredFieldDto', 'sample.OuterContainer$InnerDto',
         'sample.MismatchBaseDto', 'sample.MismatchChildDto'].each { className ->
            def cls = loader.loadClass(className)
            assert loggableType.isAssignableFrom(cls), "$className must implement Loggable"
            assert cls.getDeclaredMethods().any { m -> m.name == '_libprunus_render' && m.parameterTypes.length == 1 }, "$className must have _libprunus_render"
        }
        def serviceType = loader.loadClass('sample.SampleService')
        assert !loggableType.isAssignableFrom(serviceType), 'SampleService must not implement Loggable'
        def mismatchType = loader.loadClass('sample.MismatchChildDto')
        def mismatchText = mismatchType.getDeclaredConstructor().newInstance().toString()
        assert mismatchText.startsWith('MismatchChildDto('), 'MismatchChildDto must remain loadable and renderable when inherited private field getter descriptor mismatches'
        def userContent = new String(new File(classesDir, 'sample/UserDto.class').bytes, 'ISO-8859-1')
        assert userContent.contains('UserDto(name='), 'UserDto must render name field label'
        def largeContent = new String(new File(classesDir, 'sample/LargeDto.class').bytes, 'ISO-8859-1')
        assert largeContent.contains('LargeDto(value0='), 'LargeDto must render first of 10 fields'
        assert largeContent.contains(', value9='), 'LargeDto must render last of 10 fields'
        def pointContent = new String(new File(classesDir, 'sample/PointDto.class').bytes, 'ISO-8859-1')
        assert pointContent.contains('PointDto(x='), 'PointDto record must render x component'
        assert pointContent.contains(', y='), 'PointDto record must render y component'
        def deepContent = new String(new File(classesDir, 'sample/DeepDto.class').bytes, 'ISO-8859-1')
        assert deepContent.contains('DeepDto(deep='), 'DeepDto must render own field'
        assert deepContent.contains(', extra='), 'DeepDto must render MiddleDto.extra from inheritance'
        assert deepContent.contains(', message='), 'DeepDto must render BaseDto.message from deep inheritance'
        def filteredContent = new String(new File(classesDir, 'sample/FilteredFieldDto.class').bytes, 'ISO-8859-1')
        assert filteredContent.contains('FilteredFieldDto(visible='), 'FilteredFieldDto must render visible field'
        assert !filteredContent.contains('logger='), 'FilteredFieldDto must not render static logger field'
        assert !filteredContent.contains('$jacocoData='), 'FilteredFieldDto must not render dollar-sign field'
        def innerContent = new String(new File(classesDir, 'sample/OuterContainer$InnerDto.class').bytes, 'ISO-8859-1')
        assert innerContent.contains('InnerDto(value='), 'InnerDto must render own value field'
        assert !innerContent.contains('this$0='), 'InnerDto must not render synthetic outer reference field'
        def serviceContent = new String(new File(classesDir, 'sample/SampleService.class').bytes, 'ISO-8859-1')
        assert serviceContent.contains('|> [ENTER] SampleService.work(input='), 'SampleService.work must have entry log'
        assert serviceContent.contains('|< [EXIT] SampleService.work(value='), 'SampleService.work must have exit log'
        assert serviceContent.contains('|> [ENTER] SampleService.classify(n='), 'SampleService.classify must have entry log'
        assert serviceContent.contains('|< [EXIT] SampleService.classify(value='), 'SampleService.classify must have exit log at each return path'
''' + whitelistVerification + '''
    }
}

tasks.register('verifyAotRuntimeBinding') {
    dependsOn tasks.named('classes')
    doLast {
        def runtimeUrls = mainRuntimeClasspath.files.collect { it.toURI().toURL() } as URL[]
        def loader = new URLClassLoader(runtimeUrls, ClassLoader.systemClassLoader)
        def generatedDir = new File(project.buildDir, 'generated/classes/aot-binding/main')
        def generatedUrls = [generatedDir.toURI().toURL()] as URL[]
        def generatedLoader = new URLClassLoader(generatedUrls + runtimeUrls, ClassLoader.systemClassLoader)
        def generatedRoot = new File(generatedDir, 'org/libprunus/aot/generated')
        assert generatedRoot.isDirectory(), 'Generated AOT binding directory must exist'
        def callsiteFile = generatedRoot.listFiles().find { it.isDirectory() }?.toPath()?.resolve('RuntimeBindingCallsite.class')?.toFile()
        assert callsiteFile != null && callsiteFile.isFile(), 'Generated runtime callsite class must exist'
        def bindingId = callsiteFile.parentFile.name
        def callsiteClass = generatedLoader.loadClass("org.libprunus.aot.generated.${bindingId}.RuntimeBindingCallsite")
        callsiteClass.getDeclaredMethod('bind').invoke(null)
        def logRuntime = generatedLoader.loadClass('org.libprunus.core.log.runtime.LogRuntime')
        def globalConfigMethod = logRuntime.getDeclaredMethod('globalConfigBinding')
        globalConfigMethod.setAccessible(true)
        def globalBinding = globalConfigMethod.invoke(null)
        assert globalBinding != null, 'LogRuntime.globalConfigBinding must be initialized'
        assert globalBinding.getClass().name.endsWith('.LogConfigBindingImpl'), 'LogRuntime binding must be generated LogConfigBindingImpl'
    }
}

tasks.register('verifyAotRuntimeCallsiteDiscovery') {
    dependsOn tasks.named('classes')
    doLast {
        def runtimeUrls = mainRuntimeClasspath.files.collect { it.toURI().toURL() } as URL[]
        def generatedDir = new File(project.buildDir, 'generated/classes/aot-binding/main')
        def generatedUrls = [generatedDir.toURI().toURL()] as URL[]
        def generatedLoader = new URLClassLoader(generatedUrls + runtimeUrls, ClassLoader.systemClassLoader)

        def pointerFile = new File(generatedDir, 'META-INF/prunus/aot/runtime-binding-callsite')
        assert pointerFile.isFile(), 'Callsite pointer file must be generated'
        def spiFile = new File(generatedDir, 'META-INF/services/org.libprunus.core.log.runtime.AbstractLogConfig')
        assert spiFile.isFile(), 'SPI file must be generated'

        def logRuntime = generatedLoader.loadClass('org.libprunus.core.log.runtime.LogRuntime')
        logRuntime.getDeclaredMethod('invokeCallsiteBinding', ClassLoader).invoke(null, generatedLoader)
        def globalConfigMethod = logRuntime.getDeclaredMethod('globalConfigBinding')
        globalConfigMethod.setAccessible(true)
        def globalBinding = globalConfigMethod.invoke(null)
        assert globalBinding != null, 'LogRuntime.globalConfigBinding must be initialized via callsite discovery'
        assert globalBinding.getClass().name.endsWith('.LogConfigBindingImpl'), 'LogRuntime binding must be generated LogConfigBindingImpl after callsite discovery'
    }
}
'''
        def sourceDir = new File(projectDir, 'src/main/java/sample')
        sourceDir.mkdirs()
        new File(sourceDir, 'SampleService.java').text = '''
package sample;

public class SampleService {
    public String work(String input) {
        return input;
    }

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
'''
        new File(sourceDir, 'UserDto.java').text = '''
package sample;

import org.libprunus.core.log.annotation.DoLog;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public class UserDto {
    public String name;
    @Sensitive
    public String password;
}
'''
        new File(sourceDir, 'LargeDto.java').text = '''
package sample;

public class LargeDto {
    public String value0;
    public String value1;
    public String value2;
    public String value3;
    public String value4;
    public String value5;
    public String value6;
    public String value7;
    public String value8;
    public String value9;
}
'''
        new File(sourceDir, 'ChildDto.java').text = '''
package sample;

public class ChildDto {
    public String message;
}
'''
        new File(sourceDir, 'NestedDto.java').text = '''
package sample;

public class NestedDto {
    public String message;
    public ChildDto child;
}
'''
        new File(sourceDir, 'PointDto.java').text = '''
package sample;

public record PointDto(int x, int y) {
}
'''
        new File(sourceDir, 'BaseDto.java').text = '''
package sample;

public class BaseDto {
    public String message;
}
'''
        new File(sourceDir, 'MiddleDto.java').text = '''
package sample;

public class MiddleDto extends BaseDto {
    public String extra;
}
'''
        new File(sourceDir, 'DeepDto.java').text = '''
package sample;

public class DeepDto extends MiddleDto {
    public String deep;
}
'''
        new File(sourceDir, 'FilteredFieldDto.java').text = '''
package sample;

public class FilteredFieldDto {
    public String visible;
    public static String logger;
    public String $jacocoData;
}
'''
        new File(sourceDir, 'OuterContainer.java').text = '''
package sample;

public class OuterContainer {
    public class InnerDto {
        public String value;
    }
}
'''
        new File(sourceDir, 'MismatchUserId.java').text = '''
package sample;

public record MismatchUserId(long value) {
}
'''
        new File(sourceDir, 'MismatchBaseDto.java').text = '''
package sample;

public class MismatchBaseDto {
    private long id = 7L;

    public MismatchUserId getId() {
        return new MismatchUserId(id);
    }
}
'''
        new File(sourceDir, 'MismatchChildDto.java').text = '''
package sample;

public class MismatchChildDto extends MismatchBaseDto {
    public String name = "ok";
}
'''
    def registryDir = new File(projectDir, 'src/main/java/org/libprunus/aot')
    registryDir.mkdirs()
    new File(registryDir, 'LogContextRegistry.java').text = '''
package org.libprunus.aot;

import org.libprunus.core.log.annotation.DirectToStringWhitelist;
import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MethodLoggingProfile;
import org.libprunus.core.log.annotation.MethodLoggingProfiles;
import org.libprunus.core.log.annotation.ToStringProfile;

@LogRegistry
@MethodLoggingProfiles({
    @MethodLoggingProfile(includePackages = {"sample"}, includeClassSuffixes = {"Service", "Dto"})
})
@ToStringProfile(includePackages = {"sample"}, includeClassSuffixes = {"Dto"})
''' + whitelistAnnotation + '''
public class LogContextRegistry {
}
'''
    }

    private static void writeSampleProjectWithPropertyInspectionTask(File projectDir, File repoRoot) {
        def escapedRepoRoot = repoRoot.absolutePath.replace('\\', '\\\\')
        new File(projectDir, 'settings.gradle').text = """
rootProject.name = 'sample-app'
includeBuild('${escapedRepoRoot}')
""".stripIndent()
        new File(projectDir, 'build.gradle').text = '''
plugins {
    id 'org.libprunus.libprunus-core-plugin'
}

prunus {
    aot {
        enabled = true
        logRegistryClass = 'org.libprunus.aot.LogContextRegistry'
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.libprunus:libprunus-core:0.0.1-SNAPSHOT'
}

tasks.register('printByteBuddyClassesOutputDir') {
    doLast {
        def bbTask = tasks.getByName('byteBuddy')
        println "CLASSES_DIR_PROP=${bbTask.inputs.properties['prunusAotClassesOutputDir']}"
    }
}
'''
        def sourceDir = new File(projectDir, 'src/main/java/sample')
        sourceDir.mkdirs()
        new File(sourceDir, 'Dummy.java').text = '''
package sample;

public class Dummy {}
'''
        def registryDir = new File(projectDir, 'src/main/java/org/libprunus/aot')
        registryDir.mkdirs()
        new File(registryDir, 'LogContextRegistry.java').text = '''
package org.libprunus.aot;

import org.libprunus.core.log.annotation.LogRegistry;

@LogRegistry
public class LogContextRegistry {}
'''
    }
}
