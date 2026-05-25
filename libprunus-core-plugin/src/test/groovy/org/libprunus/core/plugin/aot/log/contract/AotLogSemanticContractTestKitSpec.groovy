package org.libprunus.core.plugin.aot.log.contract

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

class AotLogSemanticContractTestKitSpec extends Specification {

    @Shared
    @TempDir
    File sharedProjectDir

    def setupSpec() {
        ContractProjectHarness.writeBaseProject(sharedProjectDir)
        ContractProjectHarness.runCapture(sharedProjectDir)
    }

    def "toString contract: static excluded; @Sensitive masks; @DoNotLog suppresses; @DoLog passes through; across all access modifiers"() {
        when:
        def matrixDto = ContractResultReader.readToString(sharedProjectDir, 'AccessAndAnnotationMatrixDto')

        then:
        ContractResultReader.readLoggable(sharedProjectDir, 'AccessAndAnnotationMatrixDto')

        !matrixDto.contains('publicStatic=')
        !matrixDto.contains('protectedStatic=')
        !matrixDto.contains('packageStatic=')
        !matrixDto.contains('privateStatic=')

        matrixDto.contains('publicPlain=plain-public-value')
        matrixDto.contains('protectedPlain=plain-protected-value')
        matrixDto.contains('packagePlain=plain-package-value')
        matrixDto.contains('privatePlain=plain-private-value')

        matrixDto.contains('publicSensitive=***')
        matrixDto.contains('protectedSensitive=***')
        matrixDto.contains('packageSensitive=***')
        matrixDto.contains('privateSensitive=***')

        !matrixDto.contains('publicDoNotLog=')
        !matrixDto.contains('protectedDoNotLog=')
        !matrixDto.contains('packageDoNotLog=')
        !matrixDto.contains('privateDoNotLog=')

        matrixDto.contains('publicDoLog=dolog-public-value')
        matrixDto.contains('protectedDoLog=dolog-protected-value')
        matrixDto.contains('packageDoLog=dolog-package-value')
        matrixDto.contains('privateDoLog=dolog-private-value')
    }

    def "toString contract on record: static excluded; @Sensitive masks; @DoNotLog suppresses; @DoLog passes through; private-only components"() {
        when:
        def matrixRecord = ContractResultReader.readToString(sharedProjectDir, 'AccessAndAnnotationMatrixRecordDto')

        then:
        ContractResultReader.readLoggable(sharedProjectDir, 'AccessAndAnnotationMatrixRecordDto')

        !matrixRecord.contains('publicStatic=')
        !matrixRecord.contains('protectedStatic=')
        !matrixRecord.contains('packageStatic=')
        !matrixRecord.contains('privateStatic=')

        matrixRecord.contains('privatePlain=plain-private-record-value')
        matrixRecord.contains('privateSensitive=***')
        !matrixRecord.contains('privateDoNotLog=')
        matrixRecord.contains('privateDoLog=dolog-private-record-value')
    }

    def "toString contract: class-level @Sensitive - plain fields inherit MASK; field-level annotations override"() {
        when:
        def dto = ContractResultReader.readToString(sharedProjectDir, 'ClassSensitiveAccessAndAnnotationMatrixDto')

        then:
        ContractResultReader.readLoggable(sharedProjectDir, 'ClassSensitiveAccessAndAnnotationMatrixDto')

        !dto.contains('publicStatic=')
        !dto.contains('protectedStatic=')
        !dto.contains('packageStatic=')
        !dto.contains('privateStatic=')

        dto.contains('publicPlain=***')
        dto.contains('protectedPlain=***')
        dto.contains('packagePlain=***')
        dto.contains('privatePlain=***')

        dto.contains('publicSensitive=***')
        dto.contains('protectedSensitive=***')
        dto.contains('packageSensitive=***')
        dto.contains('privateSensitive=***')

        !dto.contains('publicDoNotLog=')
        !dto.contains('protectedDoNotLog=')
        !dto.contains('packageDoNotLog=')
        !dto.contains('privateDoNotLog=')

        dto.contains('publicDoLog=dolog-public-value')
        dto.contains('protectedDoLog=dolog-protected-value')
        dto.contains('packageDoLog=dolog-package-value')
        dto.contains('privateDoLog=dolog-private-value')
    }

    def "toString contract: class-level @DoNotLog - plain fields inherit SUPPRESS; field-level annotations override"() {
        when:
        def dto = ContractResultReader.readToString(sharedProjectDir, 'ClassDoNotLogAccessAndAnnotationMatrixDto')

        then:
        ContractResultReader.readLoggable(sharedProjectDir, 'ClassDoNotLogAccessAndAnnotationMatrixDto')

        !dto.contains('publicStatic=')
        !dto.contains('protectedStatic=')
        !dto.contains('packageStatic=')
        !dto.contains('privateStatic=')

        !dto.contains('publicPlain=')
        !dto.contains('protectedPlain=')
        !dto.contains('packagePlain=')
        !dto.contains('privatePlain=')

        dto.contains('publicSensitive=***')
        dto.contains('protectedSensitive=***')
        dto.contains('packageSensitive=***')
        dto.contains('privateSensitive=***')

        !dto.contains('publicDoNotLog=')
        !dto.contains('protectedDoNotLog=')
        !dto.contains('packageDoNotLog=')
        !dto.contains('privateDoNotLog=')

        dto.contains('publicDoLog=dolog-public-value')
        dto.contains('protectedDoLog=dolog-protected-value')
        dto.contains('packageDoLog=dolog-package-value')
        dto.contains('privateDoLog=dolog-private-value')
    }

    def "toString contract: class-level @DoLog - plain fields render as plain (PASS_THROUGH); field-level annotations override"() {
        when:
        def dto = ContractResultReader.readToString(sharedProjectDir, 'ClassDoLogAccessAndAnnotationMatrixDto')

        then:
        ContractResultReader.readLoggable(sharedProjectDir, 'ClassDoLogAccessAndAnnotationMatrixDto')

        !dto.contains('publicStatic=')
        !dto.contains('protectedStatic=')
        !dto.contains('packageStatic=')
        !dto.contains('privateStatic=')

        dto.contains('publicPlain=plain-public-value')
        dto.contains('protectedPlain=plain-protected-value')
        dto.contains('packagePlain=plain-package-value')
        dto.contains('privatePlain=plain-private-value')

        dto.contains('publicSensitive=***')
        dto.contains('protectedSensitive=***')
        dto.contains('packageSensitive=***')
        dto.contains('privateSensitive=***')

        !dto.contains('publicDoNotLog=')
        !dto.contains('protectedDoNotLog=')
        !dto.contains('packageDoNotLog=')
        !dto.contains('privateDoNotLog=')

        dto.contains('publicDoLog=dolog-public-value')
        dto.contains('protectedDoLog=dolog-protected-value')
        dto.contains('packageDoLog=dolog-package-value')
        dto.contains('privateDoLog=dolog-private-value')
    }

    def "toString contract on record: class-level @Sensitive - plain component inherits MASK; component-level annotations override"() {
        when:
        def rec = ContractResultReader.readToString(sharedProjectDir, 'ClassSensitiveAccessAndAnnotationMatrixRecordDto')

        then:
        ContractResultReader.readLoggable(sharedProjectDir, 'ClassSensitiveAccessAndAnnotationMatrixRecordDto')

        !rec.contains('publicStatic=')
        !rec.contains('protectedStatic=')
        !rec.contains('packageStatic=')
        !rec.contains('privateStatic=')

        rec.contains('privatePlain=***')
        rec.contains('privateSensitive=***')
        !rec.contains('privateDoNotLog=')
        rec.contains('privateDoLog=dolog-private-record-value')
    }

    def "toString contract on record: class-level @DoNotLog - plain component inherits SUPPRESS; component-level annotations override"() {
        when:
        def rec = ContractResultReader.readToString(sharedProjectDir, 'ClassDoNotLogAccessAndAnnotationMatrixRecordDto')

        then:
        ContractResultReader.readLoggable(sharedProjectDir, 'ClassDoNotLogAccessAndAnnotationMatrixRecordDto')

        !rec.contains('publicStatic=')
        !rec.contains('protectedStatic=')
        !rec.contains('packageStatic=')
        !rec.contains('privateStatic=')

        !rec.contains('privatePlain=')
        rec.contains('privateSensitive=***')
        !rec.contains('privateDoNotLog=')
        rec.contains('privateDoLog=dolog-private-record-value')
    }

    def "toString contract on record: class-level @DoLog - plain component renders as plain (PASS_THROUGH); component-level annotations override"() {
        when:
        def rec = ContractResultReader.readToString(sharedProjectDir, 'ClassDoLogAccessAndAnnotationMatrixRecordDto')

        then:
        ContractResultReader.readLoggable(sharedProjectDir, 'ClassDoLogAccessAndAnnotationMatrixRecordDto')

        !rec.contains('publicStatic=')
        !rec.contains('protectedStatic=')
        !rec.contains('packageStatic=')
        !rec.contains('privateStatic=')

        rec.contains('privatePlain=plain-private-record-value')
        rec.contains('privateSensitive=***')
        !rec.contains('privateDoNotLog=')
        rec.contains('privateDoLog=dolog-private-record-value')
    }

    def "method logging contract: only public instance methods get AOT enter/exit logs"() {
        when:
        def captured = ContractResultReader.readCallsite(sharedProjectDir, 'CallsiteAccessMatrixService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY publicStatic===',
                'INFO  boundary - ===BOUNDARY protectedStatic===',
                'INFO  boundary - ===BOUNDARY packageStatic===',
                'INFO  boundary - ===BOUNDARY privateStatic===',
                'INFO  boundary - ===BOUNDARY publicInstance===',
                'INFO  CallsiteAccessMatrixService - |> [ENTER] CallsiteAccessMatrixService.publicInstance(x=arg-publicInstance-x, s=***, l=arg-publicInstance-l)',
                'INFO  CallsiteAccessMatrixService - |< [EXIT] CallsiteAccessMatrixService.publicInstance(value=arg-publicInstance-x)',
                'INFO  boundary - ===BOUNDARY publicInstanceSensitive===',
                'INFO  CallsiteAccessMatrixService - |> [ENTER] CallsiteAccessMatrixService.publicInstanceSensitive(x=***, s=***, l=arg-publicInstanceSensitive-l)',
                'INFO  CallsiteAccessMatrixService - |< [EXIT] CallsiteAccessMatrixService.publicInstanceSensitive(value=***)',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLog===',
                'INFO  CallsiteAccessMatrixService - |> [ENTER] CallsiteAccessMatrixService.publicInstanceDoNotLog(s=***, l=arg-publicInstanceDoNotLog-l)',
                'INFO  CallsiteAccessMatrixService - |< [EXIT] CallsiteAccessMatrixService.publicInstanceDoNotLog()',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLogPure===',
                'INFO  boundary - ===BOUNDARY publicInstanceDoLog===',
                'INFO  CallsiteAccessMatrixService - |> [ENTER] CallsiteAccessMatrixService.publicInstanceDoLog(x=arg-publicInstanceDoLog-x, s=***, l=arg-publicInstanceDoLog-l)',
                'INFO  CallsiteAccessMatrixService - |< [EXIT] CallsiteAccessMatrixService.publicInstanceDoLog(value=arg-publicInstanceDoLog-x)',
                'INFO  boundary - ===BOUNDARY protectedInstance===',
                'INFO  boundary - ===BOUNDARY packageInstance===',
                'INFO  boundary - ===BOUNDARY privateInstance===',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "method logging contract: class-level @Sensitive masks parameter and return values on public instance methods"() {
        when:
        def captured = ContractResultReader.readCallsite(sharedProjectDir, 'ClassSensitiveCallsiteAccessMatrixService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY publicStatic===',
                'INFO  boundary - ===BOUNDARY protectedStatic===',
                'INFO  boundary - ===BOUNDARY packageStatic===',
                'INFO  boundary - ===BOUNDARY privateStatic===',
                'INFO  boundary - ===BOUNDARY publicInstance===',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |> [ENTER] ClassSensitiveCallsiteAccessMatrixService.publicInstance(x=***, s=***, l=arg-publicInstance-l)',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |< [EXIT] ClassSensitiveCallsiteAccessMatrixService.publicInstance(value=***)',
                'INFO  boundary - ===BOUNDARY publicInstanceSensitive===',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |> [ENTER] ClassSensitiveCallsiteAccessMatrixService.publicInstanceSensitive(x=***, s=***, l=arg-publicInstanceSensitive-l)',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |< [EXIT] ClassSensitiveCallsiteAccessMatrixService.publicInstanceSensitive(value=***)',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLog===',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |> [ENTER] ClassSensitiveCallsiteAccessMatrixService.publicInstanceDoNotLog(s=***, l=arg-publicInstanceDoNotLog-l)',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |< [EXIT] ClassSensitiveCallsiteAccessMatrixService.publicInstanceDoNotLog()',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLogPure===',
                'INFO  boundary - ===BOUNDARY publicInstanceDoLog===',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |> [ENTER] ClassSensitiveCallsiteAccessMatrixService.publicInstanceDoLog(x=arg-publicInstanceDoLog-x, s=***, l=arg-publicInstanceDoLog-l)',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |< [EXIT] ClassSensitiveCallsiteAccessMatrixService.publicInstanceDoLog(value=arg-publicInstanceDoLog-x)',
                'INFO  boundary - ===BOUNDARY protectedInstance===',
                'INFO  boundary - ===BOUNDARY packageInstance===',
                'INFO  boundary - ===BOUNDARY privateInstance===',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "method logging contract: class-level @DoNotLog - per-member resolution: closer method/parameter family annotations opt back in"() {
        when:
        def captured = ContractResultReader.readCallsite(sharedProjectDir, 'ClassDoNotLogCallsiteAccessMatrixService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY publicStatic===',
                'INFO  boundary - ===BOUNDARY protectedStatic===',
                'INFO  boundary - ===BOUNDARY packageStatic===',
                'INFO  boundary - ===BOUNDARY privateStatic===',
                'INFO  boundary - ===BOUNDARY publicInstance===',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |> [ENTER] ClassDoNotLogCallsiteAccessMatrixService.publicInstance(s=***, l=arg-publicInstance-l)',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |< [EXIT] ClassDoNotLogCallsiteAccessMatrixService.publicInstance()',
                'INFO  boundary - ===BOUNDARY publicInstanceSensitive===',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |> [ENTER] ClassDoNotLogCallsiteAccessMatrixService.publicInstanceSensitive(x=***, s=***, l=arg-publicInstanceSensitive-l)',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |< [EXIT] ClassDoNotLogCallsiteAccessMatrixService.publicInstanceSensitive(value=***)',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLog===',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |> [ENTER] ClassDoNotLogCallsiteAccessMatrixService.publicInstanceDoNotLog(s=***, l=arg-publicInstanceDoNotLog-l)',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |< [EXIT] ClassDoNotLogCallsiteAccessMatrixService.publicInstanceDoNotLog()',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLogPure===',
                'INFO  boundary - ===BOUNDARY publicInstanceDoLog===',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |> [ENTER] ClassDoNotLogCallsiteAccessMatrixService.publicInstanceDoLog(x=arg-publicInstanceDoLog-x, s=***, l=arg-publicInstanceDoLog-l)',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |< [EXIT] ClassDoNotLogCallsiteAccessMatrixService.publicInstanceDoLog(value=arg-publicInstanceDoLog-x)',
                'INFO  boundary - ===BOUNDARY protectedInstance===',
                'INFO  boundary - ===BOUNDARY packageInstance===',
                'INFO  boundary - ===BOUNDARY privateInstance===',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "method logging contract: class-level @DoLog renders parameter and return values plainly on public instance methods"() {
        when:
        def captured = ContractResultReader.readCallsite(sharedProjectDir, 'ClassDoLogCallsiteAccessMatrixService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY publicStatic===',
                'INFO  boundary - ===BOUNDARY protectedStatic===',
                'INFO  boundary - ===BOUNDARY packageStatic===',
                'INFO  boundary - ===BOUNDARY privateStatic===',
                'INFO  boundary - ===BOUNDARY publicInstance===',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |> [ENTER] ClassDoLogCallsiteAccessMatrixService.publicInstance(x=arg-publicInstance-x, s=***, l=arg-publicInstance-l)',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |< [EXIT] ClassDoLogCallsiteAccessMatrixService.publicInstance(value=arg-publicInstance-x)',
                'INFO  boundary - ===BOUNDARY publicInstanceSensitive===',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |> [ENTER] ClassDoLogCallsiteAccessMatrixService.publicInstanceSensitive(x=***, s=***, l=arg-publicInstanceSensitive-l)',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |< [EXIT] ClassDoLogCallsiteAccessMatrixService.publicInstanceSensitive(value=***)',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLog===',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |> [ENTER] ClassDoLogCallsiteAccessMatrixService.publicInstanceDoNotLog(s=***, l=arg-publicInstanceDoNotLog-l)',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |< [EXIT] ClassDoLogCallsiteAccessMatrixService.publicInstanceDoNotLog()',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLogPure===',
                'INFO  boundary - ===BOUNDARY publicInstanceDoLog===',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |> [ENTER] ClassDoLogCallsiteAccessMatrixService.publicInstanceDoLog(x=arg-publicInstanceDoLog-x, s=***, l=arg-publicInstanceDoLog-l)',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |< [EXIT] ClassDoLogCallsiteAccessMatrixService.publicInstanceDoLog(value=arg-publicInstanceDoLog-x)',
                'INFO  boundary - ===BOUNDARY protectedInstance===',
                'INFO  boundary - ===BOUNDARY packageInstance===',
                'INFO  boundary - ===BOUNDARY privateInstance===',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "toString contract: subclass of base POJO excludes private inherited fields; renders same-package package-private inherited fields per JVM accessibility"() {
        when:
        def dto = ContractResultReader.readToString(sharedProjectDir, 'ExtendedAccessAndAnnotationMatrixDto')

        then:
        ContractResultReader.readLoggable(sharedProjectDir, 'ExtendedAccessAndAnnotationMatrixDto')

        !dto.contains('publicStatic=')
        !dto.contains('protectedStatic=')
        !dto.contains('packageStatic=')
        !dto.contains('privateStatic=')

        dto.contains('publicPlain=plain-public-value')
        dto.contains('protectedPlain=plain-protected-value')
        dto.contains('packagePlain=plain-package-value')
        !dto.contains('privatePlain=')

        dto.contains('publicSensitive=***')
        dto.contains('protectedSensitive=***')
        dto.contains('packageSensitive=***')
        !dto.contains('privateSensitive=')

        !dto.contains('publicDoNotLog=')
        !dto.contains('protectedDoNotLog=')
        !dto.contains('packageDoNotLog=')
        !dto.contains('privateDoNotLog=')

        dto.contains('publicDoLog=dolog-public-value')
        dto.contains('protectedDoLog=dolog-protected-value')
        dto.contains('packageDoLog=dolog-package-value')
        !dto.contains('privateDoLog=')

        dto.contains('subPublicPlain=plain-sub-public-value')
        dto.contains('subProtectedPlain=plain-sub-protected-value')
        dto.contains('subPackagePlain=plain-sub-package-value')
        dto.contains('subPrivatePlain=plain-sub-private-value')

        dto.contains('subPublicSensitive=***')
        dto.contains('subProtectedSensitive=***')
        dto.contains('subPackageSensitive=***')
        dto.contains('subPrivateSensitive=***')

        !dto.contains('subPublicDoNotLog=')
        !dto.contains('subProtectedDoNotLog=')
        !dto.contains('subPackageDoNotLog=')
        !dto.contains('subPrivateDoNotLog=')

        dto.contains('subPublicDoLog=dolog-sub-public-value')
        dto.contains('subProtectedDoLog=dolog-sub-protected-value')
        dto.contains('subPackageDoLog=dolog-sub-package-value')
        dto.contains('subPrivateDoLog=dolog-sub-private-value')
    }

    def "toString contract: subclass of class-level @Sensitive POJO inherits MASK; excludes private inherited fields; renders same-package package-private inherited fields per JVM accessibility"() {
        when:
        def dto = ContractResultReader.readToString(sharedProjectDir, 'ExtendedClassSensitiveAccessAndAnnotationMatrixDto')

        then:
        ContractResultReader.readLoggable(sharedProjectDir, 'ExtendedClassSensitiveAccessAndAnnotationMatrixDto')

        !dto.contains('publicStatic=')
        !dto.contains('protectedStatic=')
        !dto.contains('packageStatic=')
        !dto.contains('privateStatic=')

        dto.contains('publicPlain=***')
        dto.contains('protectedPlain=***')
        dto.contains('packagePlain=***')
        !dto.contains('privatePlain=')

        dto.contains('publicSensitive=***')
        dto.contains('protectedSensitive=***')
        dto.contains('packageSensitive=***')
        !dto.contains('privateSensitive=')

        !dto.contains('publicDoNotLog=')
        !dto.contains('protectedDoNotLog=')
        !dto.contains('packageDoNotLog=')
        !dto.contains('privateDoNotLog=')

        dto.contains('publicDoLog=dolog-public-value')
        dto.contains('protectedDoLog=dolog-protected-value')
        dto.contains('packageDoLog=dolog-package-value')
        !dto.contains('privateDoLog=')

        dto.contains('subPublicPlain=plain-sub-public-value')
        dto.contains('subProtectedPlain=plain-sub-protected-value')
        dto.contains('subPackagePlain=plain-sub-package-value')
        dto.contains('subPrivatePlain=plain-sub-private-value')

        dto.contains('subPublicSensitive=***')
        dto.contains('subProtectedSensitive=***')
        dto.contains('subPackageSensitive=***')
        dto.contains('subPrivateSensitive=***')

        !dto.contains('subPublicDoNotLog=')
        !dto.contains('subProtectedDoNotLog=')
        !dto.contains('subPackageDoNotLog=')
        !dto.contains('subPrivateDoNotLog=')

        dto.contains('subPublicDoLog=dolog-sub-public-value')
        dto.contains('subProtectedDoLog=dolog-sub-protected-value')
        dto.contains('subPackageDoLog=dolog-sub-package-value')
        dto.contains('subPrivateDoLog=dolog-sub-private-value')
    }

    def "toString contract: subclass of class-level @DoNotLog POJO: plain inherits SUPPRESS; closer per-field family wins; same-package package-private inherited fields render per JVM accessibility"() {
        when:
        def dto = ContractResultReader.readToString(sharedProjectDir, 'ExtendedClassDoNotLogAccessAndAnnotationMatrixDto')

        then:
        ContractResultReader.readLoggable(sharedProjectDir, 'ExtendedClassDoNotLogAccessAndAnnotationMatrixDto')

        !dto.contains('publicStatic=')
        !dto.contains('protectedStatic=')
        !dto.contains('packageStatic=')
        !dto.contains('privateStatic=')

        !dto.contains('publicPlain=')
        !dto.contains('protectedPlain=')
        !dto.contains('packagePlain=')
        !dto.contains('privatePlain=')

        dto.contains('publicSensitive=***')
        dto.contains('protectedSensitive=***')
        dto.contains('packageSensitive=***')
        !dto.contains('privateSensitive=')

        !dto.contains('publicDoNotLog=')
        !dto.contains('protectedDoNotLog=')
        !dto.contains('packageDoNotLog=')
        !dto.contains('privateDoNotLog=')

        dto.contains('publicDoLog=dolog-public-value')
        dto.contains('protectedDoLog=dolog-protected-value')
        dto.contains('packageDoLog=dolog-package-value')
        !dto.contains('privateDoLog=')

        dto.contains('subPublicPlain=plain-sub-public-value')
        dto.contains('subProtectedPlain=plain-sub-protected-value')
        dto.contains('subPackagePlain=plain-sub-package-value')
        dto.contains('subPrivatePlain=plain-sub-private-value')

        dto.contains('subPublicSensitive=***')
        dto.contains('subProtectedSensitive=***')
        dto.contains('subPackageSensitive=***')
        dto.contains('subPrivateSensitive=***')

        !dto.contains('subPublicDoNotLog=')
        !dto.contains('subProtectedDoNotLog=')
        !dto.contains('subPackageDoNotLog=')
        !dto.contains('subPrivateDoNotLog=')

        dto.contains('subPublicDoLog=dolog-sub-public-value')
        dto.contains('subProtectedDoLog=dolog-sub-protected-value')
        dto.contains('subPackageDoLog=dolog-sub-package-value')
        dto.contains('subPrivateDoLog=dolog-sub-private-value')
    }

    def "toString contract: subclass of class-level @DoLog POJO inherits PASS_THROUGH; excludes private inherited fields; renders same-package package-private inherited fields per JVM accessibility"() {
        when:
        def dto = ContractResultReader.readToString(sharedProjectDir, 'ExtendedClassDoLogAccessAndAnnotationMatrixDto')

        then:
        ContractResultReader.readLoggable(sharedProjectDir, 'ExtendedClassDoLogAccessAndAnnotationMatrixDto')

        !dto.contains('publicStatic=')
        !dto.contains('protectedStatic=')
        !dto.contains('packageStatic=')
        !dto.contains('privateStatic=')

        dto.contains('publicPlain=plain-public-value')
        dto.contains('protectedPlain=plain-protected-value')
        dto.contains('packagePlain=plain-package-value')
        !dto.contains('privatePlain=')

        dto.contains('publicSensitive=***')
        dto.contains('protectedSensitive=***')
        dto.contains('packageSensitive=***')
        !dto.contains('privateSensitive=')

        !dto.contains('publicDoNotLog=')
        !dto.contains('protectedDoNotLog=')
        !dto.contains('packageDoNotLog=')
        !dto.contains('privateDoNotLog=')

        dto.contains('publicDoLog=dolog-public-value')
        dto.contains('protectedDoLog=dolog-protected-value')
        dto.contains('packageDoLog=dolog-package-value')
        !dto.contains('privateDoLog=')

        dto.contains('subPublicPlain=plain-sub-public-value')
        dto.contains('subProtectedPlain=plain-sub-protected-value')
        dto.contains('subPackagePlain=plain-sub-package-value')
        dto.contains('subPrivatePlain=plain-sub-private-value')

        dto.contains('subPublicSensitive=***')
        dto.contains('subProtectedSensitive=***')
        dto.contains('subPackageSensitive=***')
        dto.contains('subPrivateSensitive=***')

        !dto.contains('subPublicDoNotLog=')
        !dto.contains('subProtectedDoNotLog=')
        !dto.contains('subPackageDoNotLog=')
        !dto.contains('subPrivateDoNotLog=')

        dto.contains('subPublicDoLog=dolog-sub-public-value')
        dto.contains('subProtectedDoLog=dolog-sub-protected-value')
        dto.contains('subPackageDoLog=dolog-sub-package-value')
        dto.contains('subPrivateDoLog=dolog-sub-private-value')
    }

    def "method logging contract: subclass of base Service inherits parent instrumentation; excludes private and package-private methods"() {
        when:
        def captured = ContractResultReader.readCallsite(sharedProjectDir, 'ExtendedCallsiteAccessMatrixService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY publicStatic===',
                'INFO  boundary - ===BOUNDARY protectedStatic===',
                'INFO  boundary - ===BOUNDARY publicInstance===',
                'INFO  CallsiteAccessMatrixService - |> [ENTER] CallsiteAccessMatrixService.publicInstance(x=arg-publicInstance-x, s=***, l=arg-publicInstance-l)',
                'INFO  CallsiteAccessMatrixService - |< [EXIT] CallsiteAccessMatrixService.publicInstance(value=arg-publicInstance-x)',
                'INFO  boundary - ===BOUNDARY publicInstanceSensitive===',
                'INFO  CallsiteAccessMatrixService - |> [ENTER] CallsiteAccessMatrixService.publicInstanceSensitive(x=***, s=***, l=arg-publicInstanceSensitive-l)',
                'INFO  CallsiteAccessMatrixService - |< [EXIT] CallsiteAccessMatrixService.publicInstanceSensitive(value=***)',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLog===',
                'INFO  CallsiteAccessMatrixService - |> [ENTER] CallsiteAccessMatrixService.publicInstanceDoNotLog(s=***, l=arg-publicInstanceDoNotLog-l)',
                'INFO  CallsiteAccessMatrixService - |< [EXIT] CallsiteAccessMatrixService.publicInstanceDoNotLog()',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLogPure===',
                'INFO  boundary - ===BOUNDARY publicInstanceDoLog===',
                'INFO  CallsiteAccessMatrixService - |> [ENTER] CallsiteAccessMatrixService.publicInstanceDoLog(x=arg-publicInstanceDoLog-x, s=***, l=arg-publicInstanceDoLog-l)',
                'INFO  CallsiteAccessMatrixService - |< [EXIT] CallsiteAccessMatrixService.publicInstanceDoLog(value=arg-publicInstanceDoLog-x)',
                'INFO  boundary - ===BOUNDARY protectedInstance===',
                'INFO  boundary - ===BOUNDARY subPublicInstance===',
                'INFO  ExtendedCallsiteAccessMatrixService - |> [ENTER] ExtendedCallsiteAccessMatrixService.subPublicInstance(x=arg-subPublicInstance-x, s=***, l=arg-subPublicInstance-l)',
                'INFO  ExtendedCallsiteAccessMatrixService - |< [EXIT] ExtendedCallsiteAccessMatrixService.subPublicInstance(value=arg-subPublicInstance-x)',
                'INFO  boundary - ===BOUNDARY subPublicInstanceSensitive===',
                'INFO  ExtendedCallsiteAccessMatrixService - |> [ENTER] ExtendedCallsiteAccessMatrixService.subPublicInstanceSensitive(x=***, s=***, l=arg-subPublicInstanceSensitive-l)',
                'INFO  ExtendedCallsiteAccessMatrixService - |< [EXIT] ExtendedCallsiteAccessMatrixService.subPublicInstanceSensitive(value=***)',
                'INFO  boundary - ===BOUNDARY subPublicInstanceDoNotLog===',
                'INFO  ExtendedCallsiteAccessMatrixService - |> [ENTER] ExtendedCallsiteAccessMatrixService.subPublicInstanceDoNotLog(s=***, l=arg-subPublicInstanceDoNotLog-l)',
                'INFO  ExtendedCallsiteAccessMatrixService - |< [EXIT] ExtendedCallsiteAccessMatrixService.subPublicInstanceDoNotLog()',
                'INFO  boundary - ===BOUNDARY subPublicInstanceDoNotLogPure===',
                'INFO  boundary - ===BOUNDARY subPublicInstanceDoLog===',
                'INFO  ExtendedCallsiteAccessMatrixService - |> [ENTER] ExtendedCallsiteAccessMatrixService.subPublicInstanceDoLog(x=arg-subPublicInstanceDoLog-x, s=***, l=arg-subPublicInstanceDoLog-l)',
                'INFO  ExtendedCallsiteAccessMatrixService - |< [EXIT] ExtendedCallsiteAccessMatrixService.subPublicInstanceDoLog(value=arg-subPublicInstanceDoLog-x)',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "method logging contract: subclass of class-level @Sensitive Service inherits parent instrumentation; excludes private and package-private methods"() {
        when:
        def captured = ContractResultReader.readCallsite(sharedProjectDir, 'ExtendedClassSensitiveCallsiteAccessMatrixService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY publicStatic===',
                'INFO  boundary - ===BOUNDARY protectedStatic===',
                'INFO  boundary - ===BOUNDARY publicInstance===',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |> [ENTER] ClassSensitiveCallsiteAccessMatrixService.publicInstance(x=***, s=***, l=arg-publicInstance-l)',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |< [EXIT] ClassSensitiveCallsiteAccessMatrixService.publicInstance(value=***)',
                'INFO  boundary - ===BOUNDARY publicInstanceSensitive===',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |> [ENTER] ClassSensitiveCallsiteAccessMatrixService.publicInstanceSensitive(x=***, s=***, l=arg-publicInstanceSensitive-l)',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |< [EXIT] ClassSensitiveCallsiteAccessMatrixService.publicInstanceSensitive(value=***)',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLog===',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |> [ENTER] ClassSensitiveCallsiteAccessMatrixService.publicInstanceDoNotLog(s=***, l=arg-publicInstanceDoNotLog-l)',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |< [EXIT] ClassSensitiveCallsiteAccessMatrixService.publicInstanceDoNotLog()',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLogPure===',
                'INFO  boundary - ===BOUNDARY publicInstanceDoLog===',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |> [ENTER] ClassSensitiveCallsiteAccessMatrixService.publicInstanceDoLog(x=arg-publicInstanceDoLog-x, s=***, l=arg-publicInstanceDoLog-l)',
                'INFO  ClassSensitiveCallsiteAccessMatrixService - |< [EXIT] ClassSensitiveCallsiteAccessMatrixService.publicInstanceDoLog(value=arg-publicInstanceDoLog-x)',
                'INFO  boundary - ===BOUNDARY protectedInstance===',
                'INFO  boundary - ===BOUNDARY subPublicInstance===',
                'INFO  ExtendedClassSensitiveCallsiteAccessMatrixService - |> [ENTER] ExtendedClassSensitiveCallsiteAccessMatrixService.subPublicInstance(x=arg-subPublicInstance-x, s=***, l=arg-subPublicInstance-l)',
                'INFO  ExtendedClassSensitiveCallsiteAccessMatrixService - |< [EXIT] ExtendedClassSensitiveCallsiteAccessMatrixService.subPublicInstance(value=arg-subPublicInstance-x)',
                'INFO  boundary - ===BOUNDARY subPublicInstanceSensitive===',
                'INFO  ExtendedClassSensitiveCallsiteAccessMatrixService - |> [ENTER] ExtendedClassSensitiveCallsiteAccessMatrixService.subPublicInstanceSensitive(x=***, s=***, l=arg-subPublicInstanceSensitive-l)',
                'INFO  ExtendedClassSensitiveCallsiteAccessMatrixService - |< [EXIT] ExtendedClassSensitiveCallsiteAccessMatrixService.subPublicInstanceSensitive(value=***)',
                'INFO  boundary - ===BOUNDARY subPublicInstanceDoNotLog===',
                'INFO  ExtendedClassSensitiveCallsiteAccessMatrixService - |> [ENTER] ExtendedClassSensitiveCallsiteAccessMatrixService.subPublicInstanceDoNotLog(s=***, l=arg-subPublicInstanceDoNotLog-l)',
                'INFO  ExtendedClassSensitiveCallsiteAccessMatrixService - |< [EXIT] ExtendedClassSensitiveCallsiteAccessMatrixService.subPublicInstanceDoNotLog()',
                'INFO  boundary - ===BOUNDARY subPublicInstanceDoNotLogPure===',
                'INFO  boundary - ===BOUNDARY subPublicInstanceDoLog===',
                'INFO  ExtendedClassSensitiveCallsiteAccessMatrixService - |> [ENTER] ExtendedClassSensitiveCallsiteAccessMatrixService.subPublicInstanceDoLog(x=arg-subPublicInstanceDoLog-x, s=***, l=arg-subPublicInstanceDoLog-l)',
                'INFO  ExtendedClassSensitiveCallsiteAccessMatrixService - |< [EXIT] ExtendedClassSensitiveCallsiteAccessMatrixService.subPublicInstanceDoLog(value=arg-subPublicInstanceDoLog-x)',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "method logging contract: subclass of class-level @DoNotLog Service - per-member resolution applies through inheritance chain"() {
        when:
        def captured = ContractResultReader.readCallsite(sharedProjectDir, 'ExtendedClassDoNotLogCallsiteAccessMatrixService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY publicStatic===',
                'INFO  boundary - ===BOUNDARY protectedStatic===',
                'INFO  boundary - ===BOUNDARY publicInstance===',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |> [ENTER] ClassDoNotLogCallsiteAccessMatrixService.publicInstance(s=***, l=arg-publicInstance-l)',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |< [EXIT] ClassDoNotLogCallsiteAccessMatrixService.publicInstance()',
                'INFO  boundary - ===BOUNDARY publicInstanceSensitive===',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |> [ENTER] ClassDoNotLogCallsiteAccessMatrixService.publicInstanceSensitive(x=***, s=***, l=arg-publicInstanceSensitive-l)',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |< [EXIT] ClassDoNotLogCallsiteAccessMatrixService.publicInstanceSensitive(value=***)',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLog===',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |> [ENTER] ClassDoNotLogCallsiteAccessMatrixService.publicInstanceDoNotLog(s=***, l=arg-publicInstanceDoNotLog-l)',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |< [EXIT] ClassDoNotLogCallsiteAccessMatrixService.publicInstanceDoNotLog()',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLogPure===',
                'INFO  boundary - ===BOUNDARY publicInstanceDoLog===',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |> [ENTER] ClassDoNotLogCallsiteAccessMatrixService.publicInstanceDoLog(x=arg-publicInstanceDoLog-x, s=***, l=arg-publicInstanceDoLog-l)',
                'INFO  ClassDoNotLogCallsiteAccessMatrixService - |< [EXIT] ClassDoNotLogCallsiteAccessMatrixService.publicInstanceDoLog(value=arg-publicInstanceDoLog-x)',
                'INFO  boundary - ===BOUNDARY protectedInstance===',
                'INFO  boundary - ===BOUNDARY subPublicInstance===',
                'INFO  ExtendedClassDoNotLogCallsiteAccessMatrixService - |> [ENTER] ExtendedClassDoNotLogCallsiteAccessMatrixService.subPublicInstance(x=arg-subPublicInstance-x, s=***, l=arg-subPublicInstance-l)',
                'INFO  ExtendedClassDoNotLogCallsiteAccessMatrixService - |< [EXIT] ExtendedClassDoNotLogCallsiteAccessMatrixService.subPublicInstance(value=arg-subPublicInstance-x)',
                'INFO  boundary - ===BOUNDARY subPublicInstanceSensitive===',
                'INFO  ExtendedClassDoNotLogCallsiteAccessMatrixService - |> [ENTER] ExtendedClassDoNotLogCallsiteAccessMatrixService.subPublicInstanceSensitive(x=***, s=***, l=arg-subPublicInstanceSensitive-l)',
                'INFO  ExtendedClassDoNotLogCallsiteAccessMatrixService - |< [EXIT] ExtendedClassDoNotLogCallsiteAccessMatrixService.subPublicInstanceSensitive(value=***)',
                'INFO  boundary - ===BOUNDARY subPublicInstanceDoNotLog===',
                'INFO  ExtendedClassDoNotLogCallsiteAccessMatrixService - |> [ENTER] ExtendedClassDoNotLogCallsiteAccessMatrixService.subPublicInstanceDoNotLog(s=***, l=arg-subPublicInstanceDoNotLog-l)',
                'INFO  ExtendedClassDoNotLogCallsiteAccessMatrixService - |< [EXIT] ExtendedClassDoNotLogCallsiteAccessMatrixService.subPublicInstanceDoNotLog()',
                'INFO  boundary - ===BOUNDARY subPublicInstanceDoNotLogPure===',
                'INFO  boundary - ===BOUNDARY subPublicInstanceDoLog===',
                'INFO  ExtendedClassDoNotLogCallsiteAccessMatrixService - |> [ENTER] ExtendedClassDoNotLogCallsiteAccessMatrixService.subPublicInstanceDoLog(x=arg-subPublicInstanceDoLog-x, s=***, l=arg-subPublicInstanceDoLog-l)',
                'INFO  ExtendedClassDoNotLogCallsiteAccessMatrixService - |< [EXIT] ExtendedClassDoNotLogCallsiteAccessMatrixService.subPublicInstanceDoLog(value=arg-subPublicInstanceDoLog-x)',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "method logging contract: subclass of class-level @DoLog Service inherits parent instrumentation; excludes private and package-private methods"() {
        when:
        def captured = ContractResultReader.readCallsite(sharedProjectDir, 'ExtendedClassDoLogCallsiteAccessMatrixService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY publicStatic===',
                'INFO  boundary - ===BOUNDARY protectedStatic===',
                'INFO  boundary - ===BOUNDARY publicInstance===',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |> [ENTER] ClassDoLogCallsiteAccessMatrixService.publicInstance(x=arg-publicInstance-x, s=***, l=arg-publicInstance-l)',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |< [EXIT] ClassDoLogCallsiteAccessMatrixService.publicInstance(value=arg-publicInstance-x)',
                'INFO  boundary - ===BOUNDARY publicInstanceSensitive===',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |> [ENTER] ClassDoLogCallsiteAccessMatrixService.publicInstanceSensitive(x=***, s=***, l=arg-publicInstanceSensitive-l)',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |< [EXIT] ClassDoLogCallsiteAccessMatrixService.publicInstanceSensitive(value=***)',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLog===',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |> [ENTER] ClassDoLogCallsiteAccessMatrixService.publicInstanceDoNotLog(s=***, l=arg-publicInstanceDoNotLog-l)',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |< [EXIT] ClassDoLogCallsiteAccessMatrixService.publicInstanceDoNotLog()',
                'INFO  boundary - ===BOUNDARY publicInstanceDoNotLogPure===',
                'INFO  boundary - ===BOUNDARY publicInstanceDoLog===',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |> [ENTER] ClassDoLogCallsiteAccessMatrixService.publicInstanceDoLog(x=arg-publicInstanceDoLog-x, s=***, l=arg-publicInstanceDoLog-l)',
                'INFO  ClassDoLogCallsiteAccessMatrixService - |< [EXIT] ClassDoLogCallsiteAccessMatrixService.publicInstanceDoLog(value=arg-publicInstanceDoLog-x)',
                'INFO  boundary - ===BOUNDARY protectedInstance===',
                'INFO  boundary - ===BOUNDARY subPublicInstance===',
                'INFO  ExtendedClassDoLogCallsiteAccessMatrixService - |> [ENTER] ExtendedClassDoLogCallsiteAccessMatrixService.subPublicInstance(x=arg-subPublicInstance-x, s=***, l=arg-subPublicInstance-l)',
                'INFO  ExtendedClassDoLogCallsiteAccessMatrixService - |< [EXIT] ExtendedClassDoLogCallsiteAccessMatrixService.subPublicInstance(value=arg-subPublicInstance-x)',
                'INFO  boundary - ===BOUNDARY subPublicInstanceSensitive===',
                'INFO  ExtendedClassDoLogCallsiteAccessMatrixService - |> [ENTER] ExtendedClassDoLogCallsiteAccessMatrixService.subPublicInstanceSensitive(x=***, s=***, l=arg-subPublicInstanceSensitive-l)',
                'INFO  ExtendedClassDoLogCallsiteAccessMatrixService - |< [EXIT] ExtendedClassDoLogCallsiteAccessMatrixService.subPublicInstanceSensitive(value=***)',
                'INFO  boundary - ===BOUNDARY subPublicInstanceDoNotLog===',
                'INFO  ExtendedClassDoLogCallsiteAccessMatrixService - |> [ENTER] ExtendedClassDoLogCallsiteAccessMatrixService.subPublicInstanceDoNotLog(s=***, l=arg-subPublicInstanceDoNotLog-l)',
                'INFO  ExtendedClassDoLogCallsiteAccessMatrixService - |< [EXIT] ExtendedClassDoLogCallsiteAccessMatrixService.subPublicInstanceDoNotLog()',
                'INFO  boundary - ===BOUNDARY subPublicInstanceDoNotLogPure===',
                'INFO  boundary - ===BOUNDARY subPublicInstanceDoLog===',
                'INFO  ExtendedClassDoLogCallsiteAccessMatrixService - |> [ENTER] ExtendedClassDoLogCallsiteAccessMatrixService.subPublicInstanceDoLog(x=arg-subPublicInstanceDoLog-x, s=***, l=arg-subPublicInstanceDoLog-l)',
                'INFO  ExtendedClassDoLogCallsiteAccessMatrixService - |< [EXIT] ExtendedClassDoLogCallsiteAccessMatrixService.subPublicInstanceDoLog(value=arg-subPublicInstanceDoLog-x)',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "3-layer inheritance toString contract: inherited field policy follows the declaring class chain"() {
        expect:
        ContractResultReader.readToString(sharedProjectDir, childClass) ==
                ContractExpectations.expectedChildToString(childAnno, parentAnno, grandparentAnno)

        where:
        childAnno | parentAnno | grandparentAnno | childClass
        'Plain' | 'Plain' | 'Plain' | 'Inh3CPlainFromPPlainGpPlainSubject'
        'Plain' | 'Plain' | 'Sensitive' | 'Inh3CPlainFromPPlainGpSensitiveSubject'
        'Plain' | 'Plain' | 'DoLog' | 'Inh3CPlainFromPPlainGpDoLogSubject'
        'Plain' | 'Sensitive' | 'Plain' | 'Inh3CPlainFromPSensitiveGpPlainSubject'
        'Plain' | 'Sensitive' | 'Sensitive' | 'Inh3CPlainFromPSensitiveGpSensitiveSubject'
        'Plain' | 'Sensitive' | 'DoNotLog' | 'Inh3CPlainFromPSensitiveGpDoNotLogSubject'
        'Plain' | 'Sensitive' | 'DoLog' | 'Inh3CPlainFromPSensitiveGpDoLogSubject'
        'Plain' | 'DoLog' | 'Plain' | 'Inh3CPlainFromPDoLogGpPlainSubject'
        'Plain' | 'DoLog' | 'DoLog' | 'Inh3CPlainFromPDoLogGpDoLogSubject'
        'Sensitive' | 'Plain' | 'Plain' | 'Inh3CSensitiveFromPPlainGpPlainSubject'
        'Sensitive' | 'Plain' | 'Sensitive' | 'Inh3CSensitiveFromPPlainGpSensitiveSubject'
        'Sensitive' | 'Plain' | 'DoNotLog' | 'Inh3CSensitiveFromPPlainGpDoNotLogSubject'
        'Sensitive' | 'Plain' | 'DoLog' | 'Inh3CSensitiveFromPPlainGpDoLogSubject'
        'Sensitive' | 'Sensitive' | 'Plain' | 'Inh3CSensitiveFromPSensitiveGpPlainSubject'
        'Sensitive' | 'Sensitive' | 'Sensitive' | 'Inh3CSensitiveFromPSensitiveGpSensitiveSubject'
        'Sensitive' | 'Sensitive' | 'DoNotLog' | 'Inh3CSensitiveFromPSensitiveGpDoNotLogSubject'
        'Sensitive' | 'Sensitive' | 'DoLog' | 'Inh3CSensitiveFromPSensitiveGpDoLogSubject'
        'Sensitive' | 'DoNotLog' | 'Plain' | 'Inh3CSensitiveFromPDoNotLogGpPlainSubject'
        'Sensitive' | 'DoNotLog' | 'Sensitive' | 'Inh3CSensitiveFromPDoNotLogGpSensitiveSubject'
        'Sensitive' | 'DoNotLog' | 'DoNotLog' | 'Inh3CSensitiveFromPDoNotLogGpDoNotLogSubject'
        'Sensitive' | 'DoNotLog' | 'DoLog' | 'Inh3CSensitiveFromPDoNotLogGpDoLogSubject'
        'Sensitive' | 'DoLog' | 'Plain' | 'Inh3CSensitiveFromPDoLogGpPlainSubject'
        'Sensitive' | 'DoLog' | 'Sensitive' | 'Inh3CSensitiveFromPDoLogGpSensitiveSubject'
        'Sensitive' | 'DoLog' | 'DoNotLog' | 'Inh3CSensitiveFromPDoLogGpDoNotLogSubject'
        'Sensitive' | 'DoLog' | 'DoLog' | 'Inh3CSensitiveFromPDoLogGpDoLogSubject'
        'DoLog' | 'Plain' | 'Plain' | 'Inh3CDoLogFromPPlainGpPlainSubject'
        'DoLog' | 'Plain' | 'Sensitive' | 'Inh3CDoLogFromPPlainGpSensitiveSubject'
        'DoLog' | 'Plain' | 'DoNotLog' | 'Inh3CDoLogFromPPlainGpDoNotLogSubject'
        'DoLog' | 'Plain' | 'DoLog' | 'Inh3CDoLogFromPPlainGpDoLogSubject'
        'DoLog' | 'Sensitive' | 'Plain' | 'Inh3CDoLogFromPSensitiveGpPlainSubject'
        'DoLog' | 'Sensitive' | 'Sensitive' | 'Inh3CDoLogFromPSensitiveGpSensitiveSubject'
        'DoLog' | 'Sensitive' | 'DoNotLog' | 'Inh3CDoLogFromPSensitiveGpDoNotLogSubject'
        'DoLog' | 'Sensitive' | 'DoLog' | 'Inh3CDoLogFromPSensitiveGpDoLogSubject'
        'DoLog' | 'DoNotLog' | 'Plain' | 'Inh3CDoLogFromPDoNotLogGpPlainSubject'
        'DoLog' | 'DoNotLog' | 'Sensitive' | 'Inh3CDoLogFromPDoNotLogGpSensitiveSubject'
        'DoLog' | 'DoNotLog' | 'DoNotLog' | 'Inh3CDoLogFromPDoNotLogGpDoNotLogSubject'
        'DoLog' | 'DoNotLog' | 'DoLog' | 'Inh3CDoLogFromPDoNotLogGpDoLogSubject'
        'DoLog' | 'DoLog' | 'Plain' | 'Inh3CDoLogFromPDoLogGpPlainSubject'
        'DoLog' | 'DoLog' | 'Sensitive' | 'Inh3CDoLogFromPDoLogGpSensitiveSubject'
        'DoLog' | 'DoLog' | 'DoNotLog' | 'Inh3CDoLogFromPDoLogGpDoNotLogSubject'
        'DoLog' | 'DoLog' | 'DoLog' | 'Inh3CDoLogFromPDoLogGpDoLogSubject'
    }

    def "3-layer inheritance toString contract: SUPPRESS class short-circuit and PASS_THROUGH BFS gap cases"() {
        expect:
        ContractResultReader.readToString(sharedProjectDir, childClass) ==
                ContractExpectations.expectedChildToString(childAnno, parentAnno, grandparentAnno)

        where:
        childAnno | parentAnno | grandparentAnno | childClass
        'Plain' | 'Plain' | 'DoNotLog' | 'Inh3CPlainFromPPlainGpDoNotLogSubject'
        'Plain' | 'DoNotLog' | 'Plain' | 'Inh3CPlainFromPDoNotLogGpPlainSubject'
        'Plain' | 'DoNotLog' | 'Sensitive' | 'Inh3CPlainFromPDoNotLogGpSensitiveSubject'
        'Plain' | 'DoNotLog' | 'DoNotLog' | 'Inh3CPlainFromPDoNotLogGpDoNotLogSubject'
        'Plain' | 'DoNotLog' | 'DoLog' | 'Inh3CPlainFromPDoNotLogGpDoLogSubject'
        'Plain' | 'DoLog' | 'Sensitive' | 'Inh3CPlainFromPDoLogGpSensitiveSubject'
        'Plain' | 'DoLog' | 'DoNotLog' | 'Inh3CPlainFromPDoLogGpDoNotLogSubject'
        'DoNotLog' | 'Plain' | 'Plain' | 'Inh3CDoNotLogFromPPlainGpPlainSubject'
        'DoNotLog' | 'Plain' | 'Sensitive' | 'Inh3CDoNotLogFromPPlainGpSensitiveSubject'
        'DoNotLog' | 'Plain' | 'DoNotLog' | 'Inh3CDoNotLogFromPPlainGpDoNotLogSubject'
        'DoNotLog' | 'Plain' | 'DoLog' | 'Inh3CDoNotLogFromPPlainGpDoLogSubject'
        'DoNotLog' | 'Sensitive' | 'Plain' | 'Inh3CDoNotLogFromPSensitiveGpPlainSubject'
        'DoNotLog' | 'Sensitive' | 'Sensitive' | 'Inh3CDoNotLogFromPSensitiveGpSensitiveSubject'
        'DoNotLog' | 'Sensitive' | 'DoNotLog' | 'Inh3CDoNotLogFromPSensitiveGpDoNotLogSubject'
        'DoNotLog' | 'Sensitive' | 'DoLog' | 'Inh3CDoNotLogFromPSensitiveGpDoLogSubject'
        'DoNotLog' | 'DoNotLog' | 'Plain' | 'Inh3CDoNotLogFromPDoNotLogGpPlainSubject'
        'DoNotLog' | 'DoNotLog' | 'Sensitive' | 'Inh3CDoNotLogFromPDoNotLogGpSensitiveSubject'
        'DoNotLog' | 'DoNotLog' | 'DoNotLog' | 'Inh3CDoNotLogFromPDoNotLogGpDoNotLogSubject'
        'DoNotLog' | 'DoNotLog' | 'DoLog' | 'Inh3CDoNotLogFromPDoNotLogGpDoLogSubject'
        'DoNotLog' | 'DoLog' | 'Plain' | 'Inh3CDoNotLogFromPDoLogGpPlainSubject'
        'DoNotLog' | 'DoLog' | 'Sensitive' | 'Inh3CDoNotLogFromPDoLogGpSensitiveSubject'
        'DoNotLog' | 'DoLog' | 'DoNotLog' | 'Inh3CDoNotLogFromPDoLogGpDoNotLogSubject'
        'DoNotLog' | 'DoLog' | 'DoLog' | 'Inh3CDoNotLogFromPDoLogGpDoLogSubject'
    }

    def "3-layer inheritance callsite contract: methods follow declaring class policy; super chain emits LIFO"() {
        expect:
        ContractResultReader.readCallsite(sharedProjectDir, childClass).readLines() ==
                ContractExpectations.expectedCallsiteForChild(childAnno, parentAnno, grandparentAnno)

        where:
        childAnno | parentAnno | grandparentAnno | childClass
        'Plain' | 'Plain' | 'Plain' | 'Inh3CPlainFromPPlainGpPlainSubject'
        'Plain' | 'Plain' | 'DoNotLog' | 'Inh3CPlainFromPPlainGpDoNotLogSubject'
        'Plain' | 'Plain' | 'DoLog' | 'Inh3CPlainFromPPlainGpDoLogSubject'
        'Plain' | 'DoNotLog' | 'Plain' | 'Inh3CPlainFromPDoNotLogGpPlainSubject'
        'Plain' | 'DoNotLog' | 'Sensitive' | 'Inh3CPlainFromPDoNotLogGpSensitiveSubject'
        'Plain' | 'DoNotLog' | 'DoNotLog' | 'Inh3CPlainFromPDoNotLogGpDoNotLogSubject'
        'Plain' | 'DoNotLog' | 'DoLog' | 'Inh3CPlainFromPDoNotLogGpDoLogSubject'
        'Plain' | 'DoLog' | 'Plain' | 'Inh3CPlainFromPDoLogGpPlainSubject'
        'Plain' | 'DoLog' | 'Sensitive' | 'Inh3CPlainFromPDoLogGpSensitiveSubject'
        'Plain' | 'DoLog' | 'DoLog' | 'Inh3CPlainFromPDoLogGpDoLogSubject'
        'Sensitive' | 'Plain' | 'Plain' | 'Inh3CSensitiveFromPPlainGpPlainSubject'
        'Sensitive' | 'Plain' | 'DoNotLog' | 'Inh3CSensitiveFromPPlainGpDoNotLogSubject'
        'Sensitive' | 'Plain' | 'DoLog' | 'Inh3CSensitiveFromPPlainGpDoLogSubject'
        'Sensitive' | 'Sensitive' | 'Plain' | 'Inh3CSensitiveFromPSensitiveGpPlainSubject'
        'Sensitive' | 'Sensitive' | 'Sensitive' | 'Inh3CSensitiveFromPSensitiveGpSensitiveSubject'
        'Sensitive' | 'Sensitive' | 'DoNotLog' | 'Inh3CSensitiveFromPSensitiveGpDoNotLogSubject'
        'Sensitive' | 'Sensitive' | 'DoLog' | 'Inh3CSensitiveFromPSensitiveGpDoLogSubject'
        'Sensitive' | 'DoNotLog' | 'Plain' | 'Inh3CSensitiveFromPDoNotLogGpPlainSubject'
        'Sensitive' | 'DoNotLog' | 'Sensitive' | 'Inh3CSensitiveFromPDoNotLogGpSensitiveSubject'
        'Sensitive' | 'DoNotLog' | 'DoNotLog' | 'Inh3CSensitiveFromPDoNotLogGpDoNotLogSubject'
        'Sensitive' | 'DoNotLog' | 'DoLog' | 'Inh3CSensitiveFromPDoNotLogGpDoLogSubject'
        'Sensitive' | 'DoLog' | 'Plain' | 'Inh3CSensitiveFromPDoLogGpPlainSubject'
        'Sensitive' | 'DoLog' | 'Sensitive' | 'Inh3CSensitiveFromPDoLogGpSensitiveSubject'
        'Sensitive' | 'DoLog' | 'DoNotLog' | 'Inh3CSensitiveFromPDoLogGpDoNotLogSubject'
        'Sensitive' | 'DoLog' | 'DoLog' | 'Inh3CSensitiveFromPDoLogGpDoLogSubject'
        'DoNotLog' | 'Plain' | 'Plain' | 'Inh3CDoNotLogFromPPlainGpPlainSubject'
        'DoNotLog' | 'Plain' | 'DoNotLog' | 'Inh3CDoNotLogFromPPlainGpDoNotLogSubject'
        'DoNotLog' | 'Plain' | 'DoLog' | 'Inh3CDoNotLogFromPPlainGpDoLogSubject'
        'DoNotLog' | 'Sensitive' | 'Plain' | 'Inh3CDoNotLogFromPSensitiveGpPlainSubject'
        'DoNotLog' | 'Sensitive' | 'Sensitive' | 'Inh3CDoNotLogFromPSensitiveGpSensitiveSubject'
        'DoNotLog' | 'Sensitive' | 'DoNotLog' | 'Inh3CDoNotLogFromPSensitiveGpDoNotLogSubject'
        'DoNotLog' | 'Sensitive' | 'DoLog' | 'Inh3CDoNotLogFromPSensitiveGpDoLogSubject'
        'DoNotLog' | 'DoNotLog' | 'Plain' | 'Inh3CDoNotLogFromPDoNotLogGpPlainSubject'
        'DoNotLog' | 'DoNotLog' | 'Sensitive' | 'Inh3CDoNotLogFromPDoNotLogGpSensitiveSubject'
        'DoNotLog' | 'DoNotLog' | 'DoNotLog' | 'Inh3CDoNotLogFromPDoNotLogGpDoNotLogSubject'
        'DoNotLog' | 'DoNotLog' | 'DoLog' | 'Inh3CDoNotLogFromPDoNotLogGpDoLogSubject'
        'DoNotLog' | 'DoLog' | 'Plain' | 'Inh3CDoNotLogFromPDoLogGpPlainSubject'
        'DoNotLog' | 'DoLog' | 'Sensitive' | 'Inh3CDoNotLogFromPDoLogGpSensitiveSubject'
        'DoNotLog' | 'DoLog' | 'DoNotLog' | 'Inh3CDoNotLogFromPDoLogGpDoNotLogSubject'
        'DoNotLog' | 'DoLog' | 'DoLog' | 'Inh3CDoNotLogFromPDoLogGpDoLogSubject'
        'DoLog' | 'Plain' | 'Plain' | 'Inh3CDoLogFromPPlainGpPlainSubject'
        'DoLog' | 'Plain' | 'DoNotLog' | 'Inh3CDoLogFromPPlainGpDoNotLogSubject'
        'DoLog' | 'Plain' | 'DoLog' | 'Inh3CDoLogFromPPlainGpDoLogSubject'
        'DoLog' | 'Sensitive' | 'Plain' | 'Inh3CDoLogFromPSensitiveGpPlainSubject'
        'DoLog' | 'Sensitive' | 'Sensitive' | 'Inh3CDoLogFromPSensitiveGpSensitiveSubject'
        'DoLog' | 'Sensitive' | 'DoNotLog' | 'Inh3CDoLogFromPSensitiveGpDoNotLogSubject'
        'DoLog' | 'Sensitive' | 'DoLog' | 'Inh3CDoLogFromPSensitiveGpDoLogSubject'
        'DoLog' | 'DoNotLog' | 'Plain' | 'Inh3CDoLogFromPDoNotLogGpPlainSubject'
        'DoLog' | 'DoNotLog' | 'Sensitive' | 'Inh3CDoLogFromPDoNotLogGpSensitiveSubject'
        'DoLog' | 'DoNotLog' | 'DoNotLog' | 'Inh3CDoLogFromPDoNotLogGpDoNotLogSubject'
        'DoLog' | 'DoNotLog' | 'DoLog' | 'Inh3CDoLogFromPDoNotLogGpDoLogSubject'
        'DoLog' | 'DoLog' | 'Plain' | 'Inh3CDoLogFromPDoLogGpPlainSubject'
        'DoLog' | 'DoLog' | 'Sensitive' | 'Inh3CDoLogFromPDoLogGpSensitiveSubject'
        'DoLog' | 'DoLog' | 'DoNotLog' | 'Inh3CDoLogFromPDoLogGpDoNotLogSubject'
        'DoLog' | 'DoLog' | 'DoLog' | 'Inh3CDoLogFromPDoLogGpDoLogSubject'
    }

    def "3-layer inheritance callsite contract: method-side BFS gap cases"() {
        expect:
        ContractResultReader.readCallsite(sharedProjectDir, childClass).readLines() ==
                ContractExpectations.expectedCallsiteForChild(childAnno, parentAnno, grandparentAnno)

        where:
        childAnno | parentAnno | grandparentAnno | childClass
        'Plain' | 'Plain' | 'Sensitive' | 'Inh3CPlainFromPPlainGpSensitiveSubject'
        'Plain' | 'Sensitive' | 'Plain' | 'Inh3CPlainFromPSensitiveGpPlainSubject'
        'Plain' | 'Sensitive' | 'Sensitive' | 'Inh3CPlainFromPSensitiveGpSensitiveSubject'
        'Plain' | 'Sensitive' | 'DoNotLog' | 'Inh3CPlainFromPSensitiveGpDoNotLogSubject'
        'Plain' | 'Sensitive' | 'DoLog' | 'Inh3CPlainFromPSensitiveGpDoLogSubject'
        'Plain' | 'DoLog' | 'DoNotLog' | 'Inh3CPlainFromPDoLogGpDoNotLogSubject'
        'Sensitive' | 'Plain' | 'Sensitive' | 'Inh3CSensitiveFromPPlainGpSensitiveSubject'
        'DoNotLog' | 'Plain' | 'Sensitive' | 'Inh3CDoNotLogFromPPlainGpSensitiveSubject'
        'DoLog' | 'Plain' | 'Sensitive' | 'Inh3CDoLogFromPPlainGpSensitiveSubject'
    }

    @Shared
    @TempDir
    File familyResolutionProjectDir

    private static final List<String> FAMILY_RESOLUTION_SERVICE_FQCNS = [
            'contract.SensitiveInterfaceSubject',
            'contract.SameLayerSensitiveSubject',
            'contract.AbstractParentInheritingSubject',
            'contract.ParameterClosenessSubject',
            'contract.ReturnClosenessSubject',
            'contract.ParameterScopeSubject',
    ]

    def "family resolution: @Sensitive on declared-in interface masks parameter and return value"() {
        given:
        ensureFamilyResolutionCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(familyResolutionProjectDir, 'SensitiveInterfaceSubject')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY act===',
                'INFO  SensitiveInterfaceSubject - |> [ENTER] SensitiveInterfaceSubject.act(x=***, s=***)',
                'INFO  SensitiveInterfaceSubject - |< [EXIT] SensitiveInterfaceSubject.act(value=***)',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "family resolution: interface and superclass declaring same family at same depth resolve without conflict"() {
        given:
        ensureFamilyResolutionCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(familyResolutionProjectDir, 'SameLayerSensitiveSubject')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY process===',
                'INFO  SameLayerSensitiveSubject - |> [ENTER] SameLayerSensitiveSubject.process(x=arg-x, s=arg-s)',
                'INFO  SameLayerSensitiveSubject - |< [EXIT] SameLayerSensitiveSubject.process(value=arg-x)',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "family resolution: interface and superclass declaring different families at same depth fails the build"() {
        given:
        def variantProjectDir = java.nio.file.Files.createTempDirectory('family-resolution-buildfail-same-layer-').toFile()
        ContractProjectHarness.writeBaseProjectWithFixtures(
                variantProjectDir,
                [] as List<String>,
                ['contract.SameLayerConflictSubject'] as List<String>,
                [variantFixtureDir, '/contract/fixtures-family-resolution-buildfail-common'])

        when:
        def result = ContractProjectHarness.runBuildAndFail(variantProjectDir)

        then:
        assertBuildFailureMentions(result,
                ['mutually exclusive', 'conflict', 'multiple family', 'configuration error'],
                ['SameLayerConflictSubject', 'SameLayerConflictSensitiveInterface', 'SameLayerConflictDoLogParent'],
                ['@Sensitive', '@DoNotLog', '@DoLog'])

        cleanup:
        variantProjectDir?.deleteDir()

        where:
        variantFixtureDir << [
                '/contract/fixtures-family-resolution-buildfail-same-layer-conflict-iface-sensitive-parent-dolog',
                '/contract/fixtures-family-resolution-buildfail-same-layer-conflict-iface-dolog-parent-sensitive',
                '/contract/fixtures-family-resolution-buildfail-same-layer-conflict-iface-sensitive-parent-donotlog',
        ]
    }

    def "family resolution: abstract parent's concrete method inherited without override emits with the parent's SimpleName"() {
        given:
        ensureFamilyResolutionCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(familyResolutionProjectDir, 'AbstractParentInheritingSubject')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY compute===',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "family resolution: parameter-level annotation overrides method-level and type-level on the same parameter"() {
        given:
        ensureFamilyResolutionCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(familyResolutionProjectDir, 'ParameterClosenessSubject')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY act===',
                'INFO  ParameterClosenessSubject - |> [ENTER] ParameterClosenessSubject.act(p2=arg-p2)',
                'INFO  ParameterClosenessSubject - |< [EXIT] ParameterClosenessSubject.act(value=arg-p2)',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "family resolution: method-level annotation overrides type-level on return value"() {
        given:
        ensureFamilyResolutionCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(familyResolutionProjectDir, 'ReturnClosenessSubject')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY compute===',
                'INFO  ReturnClosenessSubject - |> [ENTER] ReturnClosenessSubject.compute(x=arg-x)',
                'INFO  ReturnClosenessSubject - |< [EXIT] ReturnClosenessSubject.compute(value=arg-x)',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "family resolution: a field carrying two family annotations fails the build"() {
        given:
        def variantProjectDir = java.nio.file.Files.createTempDirectory('family-resolution-buildfail-multi-family-').toFile()
        ContractProjectHarness.writeBaseProjectWithFixtures(
                variantProjectDir,
                ['contract.MultiFamilyOnFieldDto'] as List<String>,
                [] as List<String>,
                [variantFixtureDir, '/contract/fixtures-family-resolution-buildfail-common'])

        when:
        def result = ContractProjectHarness.runBuildAndFail(variantProjectDir)

        then:
        assertBuildFailureMentions(result,
                ['mutually exclusive', 'conflict', 'multiple family', 'configuration error'],
                ['MultiFamilyOnFieldDto', 'collidingField'],
                ['@Sensitive', '@DoNotLog', '@DoLog'])

        cleanup:
        variantProjectDir?.deleteDir()

        where:
        variantFixtureDir << [
                '/contract/fixtures-family-resolution-buildfail-multi-family-target-sensitive-dolog',
                '/contract/fixtures-family-resolution-buildfail-multi-family-target-sensitive-donotlog',
                '/contract/fixtures-family-resolution-buildfail-multi-family-target-dolog-donotlog',
        ]
    }

    def "family resolution: PARAMETER target is ignored on constructors and non-public methods"() {
        given:
        ensureFamilyResolutionCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(familyResolutionProjectDir, 'ParameterScopeSubject')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY ctor===',
                'INFO  boundary - ===BOUNDARY publicMethod===',
                'INFO  ParameterScopeSubject - |> [ENTER] ParameterScopeSubject.publicMethod(s=arg-public-s)',
                'INFO  ParameterScopeSubject - |< [EXIT] ParameterScopeSubject.publicMethod(value=arg-public-s)',
                'INFO  boundary - ===BOUNDARY packageMethod===',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    private void ensureFamilyResolutionCaptureReady() {
        if (new File(familyResolutionProjectDir, 'build/contract-results').isDirectory()) {
            return
        }
        ContractProjectHarness.writeBaseProjectWithFixtures(
                familyResolutionProjectDir,
                [] as List<String>,
                FAMILY_RESOLUTION_SERVICE_FQCNS,
                ['/contract/fixtures-family-resolution'])
        ContractProjectHarness.runCapture(familyResolutionProjectDir)
    }

    @Shared
    @TempDir
    File profileMatchingMatrixProjectDir

    private static final List<String> PROFILE_MATCHING_MATRIX_DTO_FQCNS = [
            'com.example.ProfileMatchExampleDto',
            'com.example.sub.ProfileMatchSubpackageDto',
            'com.example.internal.ProfileMatchInternalDto',
            'com.exampleother.ProfileMatchSiblingDto',
            'com.beta.ProfileMatchBetaDto',
            'com.example.ProfileMatchExampleResponse',
            'com.example.ProfileMatchUnmatchedSubject',
    ]

    private static final List<String> PROFILE_MATCHING_MATRIX_SERVICE_FQCNS = [
            'com.example.ProfileMatchExampleService',
            'com.beta.ProfileMatchBetaService',
    ]

    private void ensureProfileMatchingMatrixCaptureReady() {
        if (new File(profileMatchingMatrixProjectDir, 'build/contract-results').isDirectory()) {
            return
        }
        ContractProjectHarness.writeBaseProjectWithMultiPackageFixtures(
                profileMatchingMatrixProjectDir,
                PROFILE_MATCHING_MATRIX_DTO_FQCNS,
                PROFILE_MATCHING_MATRIX_SERVICE_FQCNS,
                ['/contract/fixtures-profile-matching-positive-matrix'],
                'com.example.registry.LogContextRegistry')
        ContractProjectHarness.runCapture(profileMatchingMatrixProjectDir)
    }

    def "profile matching: includePackages prefix does not match a sibling package with a longer name"() {
        given:
        ensureProfileMatchingMatrixCaptureReady()

        expect:
        ContractResultReader.readLoggable(profileMatchingMatrixProjectDir, 'ProfileMatchExampleDto')
        ContractResultReader.readToString(profileMatchingMatrixProjectDir, 'ProfileMatchExampleDto')
                .startsWith('ProfileMatchExampleDto(')

        and:
        ContractResultReader.readLoggable(profileMatchingMatrixProjectDir, 'ProfileMatchSubpackageDto')
        ContractResultReader.readToString(profileMatchingMatrixProjectDir, 'ProfileMatchSubpackageDto')
                .startsWith('ProfileMatchSubpackageDto(')

        and:
        !ContractResultReader.readLoggable(profileMatchingMatrixProjectDir, 'ProfileMatchSiblingDto')
        !ContractResultReader.readToString(profileMatchingMatrixProjectDir, 'ProfileMatchSiblingDto')
                .startsWith('ProfileMatchSiblingDto(')
        ContractResultReader.readToString(profileMatchingMatrixProjectDir, 'ProfileMatchSiblingDto')
                .contains('com.exampleother.ProfileMatchSiblingDto@')
    }

    def "profile matching: excludePackages prefix removes a child subpackage from the include set"() {
        given:
        ensureProfileMatchingMatrixCaptureReady()

        expect:
        !ContractResultReader.readLoggable(profileMatchingMatrixProjectDir, 'ProfileMatchInternalDto')
        !ContractResultReader.readToString(profileMatchingMatrixProjectDir, 'ProfileMatchInternalDto')
                .startsWith('ProfileMatchInternalDto(')
        ContractResultReader.readToString(profileMatchingMatrixProjectDir, 'ProfileMatchInternalDto')
                .contains('com.example.internal.ProfileMatchInternalDto@')
    }

    def "profile matching: includeClassSuffixes accepts any of the listed suffixes"() {
        given:
        ensureProfileMatchingMatrixCaptureReady()

        expect:
        ContractResultReader.readLoggable(profileMatchingMatrixProjectDir, 'ProfileMatchExampleDto')
        ContractResultReader.readToString(profileMatchingMatrixProjectDir, 'ProfileMatchExampleDto')
                .startsWith('ProfileMatchExampleDto(')

        and:
        ContractResultReader.readLoggable(profileMatchingMatrixProjectDir, 'ProfileMatchExampleResponse')
        ContractResultReader.readToString(profileMatchingMatrixProjectDir, 'ProfileMatchExampleResponse')
                .startsWith('ProfileMatchExampleResponse(')
    }

    def "profile matching: ToStringProfile not matching a class leaves the original toString untouched"() {
        given:
        ensureProfileMatchingMatrixCaptureReady()

        expect:
        !ContractResultReader.readLoggable(profileMatchingMatrixProjectDir, 'ProfileMatchUnmatchedSubject')

        and:
        def rendered = ContractResultReader.readToString(profileMatchingMatrixProjectDir, 'ProfileMatchUnmatchedSubject')
        !rendered.startsWith('ProfileMatchUnmatchedSubject(')
        rendered.contains('com.example.ProfileMatchUnmatchedSubject@')
    }

    def "profile matching: MethodLoggingProfile not matching a class emits no ENTER or EXIT"() {
        given:
        ensureProfileMatchingMatrixCaptureReady()

        expect:
        ContractResultReader.readCallsite(profileMatchingMatrixProjectDir, 'ProfileMatchExampleService').readLines() == [
                'INFO  boundary - ===BOUNDARY run===',
                'INFO  ProfileMatchExampleService - |> [ENTER] ProfileMatchExampleService.run(x=arg-run)',
                'INFO  ProfileMatchExampleService - |< [EXIT] ProfileMatchExampleService.run(value=arg-run)',
                'INFO  boundary - ===BOUNDARY END===',
        ]

        and:
        ContractResultReader.readCallsite(profileMatchingMatrixProjectDir, 'ProfileMatchBetaService').readLines() == [
                'INFO  boundary - ===BOUNDARY run===',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "profile matching: declaration order of multiple non-conflicting profiles has no semantic effect"() {
        given:
        ensureProfileMatchingMatrixCaptureReady()

        expect:
        ContractResultReader.readLoggable(profileMatchingMatrixProjectDir, 'ProfileMatchExampleDto')
        ContractResultReader.readToString(profileMatchingMatrixProjectDir, 'ProfileMatchExampleDto')
                .startsWith('ProfileMatchExampleDto(')

        and:
        ContractResultReader.readLoggable(profileMatchingMatrixProjectDir, 'ProfileMatchBetaDto')
        ContractResultReader.readToString(profileMatchingMatrixProjectDir, 'ProfileMatchBetaDto')
                .startsWith('ProfileMatchBetaDto(')
    }

    @Shared
    @TempDir
    File profileMatchingTrailingDotProjectDir

    def "profile matching: includePackages prefix with trailing dot is equivalent to without"() {
        given:
        ContractProjectHarness.writeBaseProjectWithMultiPackageFixtures(
                profileMatchingTrailingDotProjectDir,
                ['com.example.TrailingDotMatchedDto', 'com.example.sub.TrailingDotSubpackageDto'] as List<String>,
                [] as List<String>,
                ['/contract/fixtures-profile-matching-trailing-dot'],
                'com.example.registry.LogContextRegistry')
        ContractProjectHarness.runCapture(profileMatchingTrailingDotProjectDir)

        expect:
        ContractResultReader.readLoggable(profileMatchingTrailingDotProjectDir, 'TrailingDotMatchedDto')
        ContractResultReader.readToString(profileMatchingTrailingDotProjectDir, 'TrailingDotMatchedDto')
                .startsWith('TrailingDotMatchedDto(')

        and:
        ContractResultReader.readLoggable(profileMatchingTrailingDotProjectDir, 'TrailingDotSubpackageDto')
        ContractResultReader.readToString(profileMatchingTrailingDotProjectDir, 'TrailingDotSubpackageDto')
                .startsWith('TrailingDotSubpackageDto(')
    }

    @Shared
    @TempDir
    File profileMatchingEmptyIncludePackagesProjectDir

    def "profile matching: empty includePackages on ToStringProfile matches no class"() {
        given:
        ContractProjectHarness.writeBaseProjectWithMultiPackageFixtures(
                profileMatchingEmptyIncludePackagesProjectDir,
                ['com.example.EmptyIncludePackagesDto'] as List<String>,
                [] as List<String>,
                ['/contract/fixtures-profile-matching-empty-include-packages'],
                'com.example.registry.LogContextRegistry')
        ContractProjectHarness.runCapture(profileMatchingEmptyIncludePackagesProjectDir)

        expect:
        !ContractResultReader.readLoggable(profileMatchingEmptyIncludePackagesProjectDir, 'EmptyIncludePackagesDto')

        and:
        def rendered = ContractResultReader.readToString(profileMatchingEmptyIncludePackagesProjectDir, 'EmptyIncludePackagesDto')
        !rendered.startsWith('EmptyIncludePackagesDto(')
        rendered.contains('com.example.EmptyIncludePackagesDto@')
    }

    @Shared
    @TempDir
    File profileMatchingEmptySuffixesProjectDir

    def "profile matching: empty includeClassSuffixes on ToStringProfile matches no class"() {
        given:
        ContractProjectHarness.writeBaseProjectWithMultiPackageFixtures(
                profileMatchingEmptySuffixesProjectDir,
                ['com.example.EmptySuffixesDto'] as List<String>,
                [] as List<String>,
                ['/contract/fixtures-profile-matching-empty-suffixes'],
                'com.example.registry.LogContextRegistry')
        ContractProjectHarness.runCapture(profileMatchingEmptySuffixesProjectDir)

        expect:
        !ContractResultReader.readLoggable(profileMatchingEmptySuffixesProjectDir, 'EmptySuffixesDto')

        and:
        def rendered = ContractResultReader.readToString(profileMatchingEmptySuffixesProjectDir, 'EmptySuffixesDto')
        !rendered.startsWith('EmptySuffixesDto(')
        rendered.contains('com.example.EmptySuffixesDto@')
    }

    def "profile matching: two ToStringProfile declarations matching the same class fails the build"() {
        given:
        def variantProjectDir = java.nio.file.Files.createTempDirectory('profile-matching-buildfail-multi-tostring-').toFile()
        ContractProjectHarness.writeBaseProjectWithMultiPackageFixtures(
                variantProjectDir,
                ['com.example.MultiToStringProfileTarget'] as List<String>,
                [] as List<String>,
                ['/contract/fixtures-profile-matching-buildfail-multi-tostring'],
                'com.example.registry.LogContextRegistry')

        when:
        def result = ContractProjectHarness.runBuildAndFail(variantProjectDir)

        then:
        assertBuildFailureMentions(result,
                ['mutually exclusive', 'conflict', 'multiple', 'configuration error', 'matches'],
                ['MultiToStringProfileTarget', 'com.example.MultiToStringProfileTarget'],
                ['@ToStringProfile', 'ToStringProfile'])

        cleanup:
        variantProjectDir?.deleteDir()
    }

    def "profile matching: two MethodLoggingProfile declarations matching the same class fails the build"() {
        given:
        def variantProjectDir = java.nio.file.Files.createTempDirectory('profile-matching-buildfail-multi-method-logging-').toFile()
        ContractProjectHarness.writeBaseProjectWithMultiPackageFixtures(
                variantProjectDir,
                [] as List<String>,
                ['com.example.MultiMethodLoggingProfileTarget'] as List<String>,
                ['/contract/fixtures-profile-matching-buildfail-multi-method-logging'],
                'com.example.registry.LogContextRegistry')

        when:
        def result = ContractProjectHarness.runBuildAndFail(variantProjectDir)

        then:
        assertBuildFailureMentions(result,
                ['mutually exclusive', 'conflict', 'multiple', 'configuration error', 'matches'],
                ['MultiMethodLoggingProfileTarget', 'com.example.MultiMethodLoggingProfileTarget'],
                ['@MethodLoggingProfile', 'MethodLoggingProfile'])

        cleanup:
        variantProjectDir?.deleteDir()
    }

    def "method logging field: fields() referencing an unknown MethodLoggingField name fails the build"() {
        given:
        def variantProjectDir = java.nio.file.Files.createTempDirectory('method-logging-field-buildfail-unknown-field-').toFile()
        ContractProjectHarness.writeBaseProjectWithMultiPackageFixtures(
                variantProjectDir,
                [] as List<String>,
                ['com.example.UnknownFieldReferenceService'] as List<String>,
                ['/contract/fixtures-method-logging-field-buildfail-unknown-field'],
                'com.example.registry.LogContextRegistry')

        when:
        def result = ContractProjectHarness.runBuildAndFail(variantProjectDir)

        then:
        assertBuildFailureMentions(result,
                ['unknown', 'not declared', 'not found', 'undefined', 'configuration error', 'no such'],
                ['requestId'],
                ['@MethodLoggingField', 'MethodLoggingField', '@MethodLoggingProfile', 'MethodLoggingProfile'])

        cleanup:
        variantProjectDir?.deleteDir()
    }

    def "method logging field: two @MethodLoggingField declarations sharing the same value() fails the build"() {
        given:
        def variantProjectDir = java.nio.file.Files.createTempDirectory('method-logging-field-buildfail-duplicate-value-').toFile()
        ContractProjectHarness.writeBaseProjectWithMultiPackageFixtures(
                variantProjectDir,
                [] as List<String>,
                ['com.example.DuplicateMethodLoggingFieldValueService'] as List<String>,
                ['/contract/fixtures-method-logging-field-buildfail-duplicate-value'],
                'com.example.registry.LogContextRegistry')

        when:
        def result = ContractProjectHarness.runBuildAndFail(variantProjectDir)

        then:
        assertBuildFailureMentions(result,
                ['duplicate', 'unique', 'conflict', 'already', 'configuration error'],
                ['traceId'],
                ['@MethodLoggingField', 'MethodLoggingField'])

        cleanup:
        variantProjectDir?.deleteDir()
    }

    def "method logging field: violating method shape constraints fails the build"() {
        given:
        def variantProjectDir = java.nio.file.Files.createTempDirectory("method-logging-field-buildfail-${variantSlug}-").toFile()
        ContractProjectHarness.writeBaseProjectWithMultiPackageFixtures(
                variantProjectDir,
                [] as List<String>,
                ["com.example.${variantServiceSimpleName}"] as List<String>,
                ["/contract/fixtures-method-logging-field-buildfail-${variantSlug}"],
                'com.example.registry.LogContextRegistry')

        when:
        def result = ContractProjectHarness.runBuildAndFail(variantProjectDir)

        then:
        assertBuildFailureMentions(result,
                ['public', 'static', 'parameter', 'return', 'void', 'configuration error', 'must'],
                ['traceId'],
                ['@MethodLoggingField', 'MethodLoggingField'])

        cleanup:
        variantProjectDir?.deleteDir()

        where:
        variantSlug      | variantServiceSimpleName
        'non-public'     | 'NonPublicFieldMethodService'
        'non-static'     | 'NonStaticFieldMethodService'
        'with-params'    | 'FieldMethodWithParamsService'
        'void-return'    | 'VoidReturnFieldMethodService'
    }

    @Shared
    @TempDir
    File methodShapeRenderProjectDir

    private static final List<String> METHOD_SHAPE_SERVICE_FQCNS = [
            'contract.MethodEligibleShapesService',
            'contract.VoidMethodService',
            'contract.ThrowingMethodService',
    ]

    private void ensureMethodShapeRenderCaptureReady() {
        if (new File(methodShapeRenderProjectDir, 'build/contract-results').isDirectory()) {
            return
        }
        ContractProjectHarness.writeBaseProjectWithFixtures(
                methodShapeRenderProjectDir,
                [] as List<String>,
                METHOD_SHAPE_SERVICE_FQCNS,
                ['/contract/fixtures-method-shape-render'])
        ContractProjectHarness.runCapture(methodShapeRenderProjectDir)
    }

    @Shared
    @TempDir
    File shadowFieldRenderProjectDir

    private static final List<String> SHADOW_FIELD_DTO_FQCNS = [
            'contract.ShadowSameFamilyChildDto',
            'contract.ShadowSameFamilyParentDto',
            'contract.ShadowCrossFamilyChildDto',
            'contract.ShadowCrossFamilyParentDto',
            'contract.SensitiveInterfaceRecord',
            'contract.NullFieldRenderingDto',
            'contract.TransientDollarPrefixDto',
            'contract.OuterWithInnerDto',
    ]

    private void ensureShadowFieldRenderCaptureReady() {
        if (new File(shadowFieldRenderProjectDir, 'build/contract-results').isDirectory()) {
            return
        }
        ContractProjectHarness.writeBaseProjectWithFixtures(
                shadowFieldRenderProjectDir,
                SHADOW_FIELD_DTO_FQCNS,
                [] as List<String>,
                ['/contract/fixtures-shadow-field-render'])
        ContractProjectHarness.runCapture(shadowFieldRenderProjectDir)
    }

    def "toString contract: subclass redeclaring an inherited field name renders both with declaring-class parenthetical qualifier"() {
        given:
        ensureShadowFieldRenderCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(shadowFieldRenderProjectDir, 'ShadowSameFamilyChildDto')

        then:
        ContractResultReader.readLoggable(shadowFieldRenderProjectDir, 'ShadowSameFamilyChildDto')

        rendered.startsWith('ShadowSameFamilyChildDto(')
        rendered.endsWith(')')

        rendered.contains('name(ShadowSameFamilyChildDto)=***')
        rendered.contains('name(ShadowSameFamilyParentDto)=parent-val')
        rendered.contains('childOnly=child-only-val')
        rendered.contains('parentOnly=parent-only-val')

        !rendered.contains('childOnly(')
        !rendered.contains('parentOnly(')

        rendered.indexOf('name(ShadowSameFamilyChildDto)') < rendered.indexOf('name(ShadowSameFamilyParentDto)')
        rendered.indexOf('childOnly=') < rendered.indexOf('parentOnly=')
    }

    def "toString contract: shadowed fields across layers each resolve their own family annotation independently"() {
        given:
        ensureShadowFieldRenderCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(shadowFieldRenderProjectDir, 'ShadowCrossFamilyChildDto')

        then:
        ContractResultReader.readLoggable(shadowFieldRenderProjectDir, 'ShadowCrossFamilyChildDto')

        rendered.startsWith('ShadowCrossFamilyChildDto(')
        rendered.endsWith(')')

        rendered.contains('data(ShadowCrossFamilyChildDto)=child-val')
        rendered.contains('data(ShadowCrossFamilyParentDto)=***')

        rendered.indexOf('data(ShadowCrossFamilyChildDto)') < rendered.indexOf('data(ShadowCrossFamilyParentDto)')
    }

    def "toString contract on record: record implementing @Sensitive interface masks all components per the interface annotation"() {
        given:
        ensureShadowFieldRenderCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(shadowFieldRenderProjectDir, 'SensitiveInterfaceRecord')

        then:
        ContractResultReader.readLoggable(shadowFieldRenderProjectDir, 'SensitiveInterfaceRecord')

        rendered.startsWith('SensitiveInterfaceRecord(')
        rendered.endsWith(')')

        rendered.contains('a=alpha-val')
        rendered.contains('b=beta-val')
    }

    def "method logging contract: only public instance methods get ENTER/EXIT (every excluded method shape verified)"() {
        given:
        ensureMethodShapeRenderCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(methodShapeRenderProjectDir, 'MethodEligibleShapesService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY publicStaticCall===',
                'INFO  boundary - ===BOUNDARY constructorCall===',
                'INFO  boundary - ===BOUNDARY publicInstanceCall===',
                'INFO  MethodEligibleShapesService - |> [ENTER] MethodEligibleShapesService.publicInstance(x=arg-publicInstance)',
                'INFO  MethodEligibleShapesService - |< [EXIT] MethodEligibleShapesService.publicInstance(value=arg-publicInstance-result)',
                'INFO  boundary - ===BOUNDARY protectedInstanceCall===',
                'INFO  boundary - ===BOUNDARY packageInstanceCall===',
                'INFO  boundary - ===BOUNDARY privateInstanceCall===',
                'INFO  boundary - ===BOUNDARY syntheticLambdaCall===',
                'INFO  boundary - ===BOUNDARY equalsCall===',
                'INFO  boundary - ===BOUNDARY hashCodeCall===',
                'INFO  boundary - ===BOUNDARY toStringCall===',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "toString contract: null field value renders verbatim under @DoLog, masked under @Sensitive, dropped under @DoNotLog"() {
        given:
        ensureShadowFieldRenderCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(shadowFieldRenderProjectDir, 'NullFieldRenderingDto')

        then:
        ContractResultReader.readLoggable(shadowFieldRenderProjectDir, 'NullFieldRenderingDto')

        rendered.startsWith('NullFieldRenderingDto(')
        rendered.endsWith(')')

        rendered.contains('a=null')
        rendered.contains('b=***')
        rendered.contains('d=x')

        !rendered.contains('c=')
    }

    def "method logging contract: a void-returning public instance method emits ENTER and EXIT but with no value slot"() {
        given:
        ensureMethodShapeRenderCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(methodShapeRenderProjectDir, 'VoidMethodService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY doVoid===',
                'INFO  VoidMethodService - |> [ENTER] VoidMethodService.doVoid(x=arg-doVoid)',
                'INFO  VoidMethodService - |< [EXIT] VoidMethodService.doVoid()',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    def "method logging contract: a method that throws an exception emits ENTER but no EXIT; the exception propagates"() {
        given:
        ensureMethodShapeRenderCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(methodShapeRenderProjectDir, 'ThrowingMethodService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY throwing===',
                'INFO  ThrowingMethodService - |> [ENTER] ThrowingMethodService.throwing(x=arg-throwing)',
                'INFO  boundary - ===CAUGHT java.lang.IllegalStateException: bang: arg-throwing===',
                'INFO  boundary - ===BOUNDARY END===',
        ]
    }

    private static void assertBuildFailureMentions(org.gradle.testkit.runner.BuildResult result,
                                                    List<String> lowerKeywordsAny,
                                                    List<String> entityNamesAny,
                                                    List<String> annotationNamesAny) {
        assert !result.output.contains('BUILD SUCCESSFUL')
        String lowered = result.output.toLowerCase()
        assert lowerKeywordsAny.any { lowered.contains(it) },
                "expected build output to mention any of ${lowerKeywordsAny} (lowercased)"
        assert entityNamesAny.any { result.output.contains(it) },
                "expected build output to mention any of ${entityNamesAny}"
        assert annotationNamesAny.any { result.output.contains(it) },
                "expected build output to mention any of ${annotationNamesAny}"
    }

    private static String logbackXml(String rootLevel, String encoderPattern) {
        """<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${encoderPattern}</pattern>
        </encoder>
    </appender>
    <root level="${rootLevel}">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
"""
    }

    private static String stdLogbackXml(String rootLevel) {
        logbackXml(rootLevel, '%-5level %logger{0} - %msg%n')
    }

    private static String kvpSuppressedLogbackXml(String rootLevel) {
        logbackXml(rootLevel, '%-5level %logger{0} - %msg %kvp{NONE}%n')
    }

    private static final String KVP_SUPPRESSED_LOGBACK_XML = kvpSuppressedLogbackXml('INFO')

    @Shared
    @TempDir
    File automatedEscapeHatchProjectDir

    private static final List<String> AUTOMATED_ESCAPE_HATCH_DTO_FQCNS = [
            'contract.AutomatedProcessingIgnoreClassSubject',
            'contract.AutomatedProcessingIgnoreMethodSubject',
            'contract.AutomatedProcessingIgnoreNotInheritedChild',
            'contract.AutomatedTypeIgnoreSensitiveFieldsSubject',
            'contract.AutomatedMethodIgnoreSiblingFieldsRemainSensitiveSubject',
    ]

    private static final List<String> AUTOMATED_ESCAPE_HATCH_SERVICE_FQCNS = [
            'contract.AutomatedProcessingIgnoreClassSubject',
            'contract.AutomatedProcessingIgnoreMethodSubject',
            'contract.AutomatedProcessingIgnoreNotInheritedChild',
            'contract.AutomatedTypeIgnoreSensitiveParamSubject',
            'contract.AutomatedMethodIgnoreOverridesClassDoNotLogSubject',
            'contract.AutomatedMethodIgnoreOverridesClassSensitiveSubject',
            'contract.AutomatedMethodIgnoreSiblingFieldsRemainSensitiveSubject',
            'contract.AutomatedMethodIgnoreWithSensitiveParamSubject',
            'contract.AutomatedPrivateMethodIgnoreSubject',
            'contract.AutomatedStaticMethodIgnoreSubject',
            'contract.AutomatedMethodIgnoreOverridesIfaceDoNotLogSubject',
    ]

    private void ensureAutomatedEscapeHatchCaptureReady() {
        if (new File(automatedEscapeHatchProjectDir, 'build/contract-results').isDirectory()) {
            return
        }
        ContractProjectHarness.writeBaseProjectWithFixturesAndCustomLogback(
                automatedEscapeHatchProjectDir,
                AUTOMATED_ESCAPE_HATCH_DTO_FQCNS,
                AUTOMATED_ESCAPE_HATCH_SERVICE_FQCNS,
                ['/contract/fixtures-automated-escape-hatch'],
                'contract.LogContextRegistry',
                KVP_SUPPRESSED_LOGBACK_XML)
        ContractProjectHarness.runCapture(automatedEscapeHatchProjectDir)
    }

    @Shared
    @TempDir
    File methodLoggingFieldExtractorsProjectDir

    private static final List<String> METHOD_LOGGING_FIELD_EXTRACTORS_SERVICE_FQCNS = [
            'contract.ExtractorStringValueService',
            'contract.ExtractorNullValueService',
            'contract.ExtractorPrimitiveValueService',
            'contract.ExtractorThrowingService',
    ]

    private void ensureMethodLoggingFieldExtractorsCaptureReady() {
        if (new File(methodLoggingFieldExtractorsProjectDir, 'build/contract-results').isDirectory()) {
            return
        }
        ContractProjectHarness.writeBaseProjectWithFixturesAndCustomLogback(
                methodLoggingFieldExtractorsProjectDir,
                [] as List<String>,
                METHOD_LOGGING_FIELD_EXTRACTORS_SERVICE_FQCNS,
                ['/contract/fixtures-method-logging-field-extractors'],
                'contract.LogContextRegistry',
                KVP_SUPPRESSED_LOGBACK_XML)
        ContractProjectHarness.runCapture(methodLoggingFieldExtractorsProjectDir)
    }

    @Shared
    @TempDir
    File unreferencedFieldsProjectDir

    private static final List<String> UNREFERENCED_FIELDS_SERVICE_FQCNS = [
            'contract.UnreferencedFieldService',
    ]

    private void ensureUnreferencedFieldsCaptureReady() {
        if (new File(unreferencedFieldsProjectDir, 'build/contract-results').isDirectory()) {
            return
        }
        ContractProjectHarness.writeBaseProjectWithFixturesAndCustomLogback(
                unreferencedFieldsProjectDir,
                [] as List<String>,
                UNREFERENCED_FIELDS_SERVICE_FQCNS,
                ['/contract/fixtures-unreferenced-fields'],
                'contract.LogContextRegistry',
                KVP_SUPPRESSED_LOGBACK_XML)
        ContractProjectHarness.runCapture(unreferencedFieldsProjectDir)
    }

    def "toString contract: transient and dollar-prefixed fields are excluded from toString"() {
        given:
        ensureShadowFieldRenderCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(shadowFieldRenderProjectDir, 'TransientDollarPrefixDto')

        then:
        ContractResultReader.readLoggable(shadowFieldRenderProjectDir, 'TransientDollarPrefixDto')

        rendered.startsWith('TransientDollarPrefixDto(')
        rendered.endsWith(')')

        rendered.contains('normalField=7')

        !rendered.contains('tx=')
        !rendered.contains('$dollarPrefix=')
        !rendered.contains('dollarPrefix=')
    }

    def "toString contract: inherited package-private field declared in a different package is excluded from subclass toString"() {
        given:
        def crossPackageProjectDir = java.nio.file.Files.createTempDirectory('cross-package-package-private-excluded-').toFile()
        ContractProjectHarness.writeBaseProjectWithMultiPackageFixtures(
                crossPackageProjectDir,
                ['contract.child.CrossPackagePrivateChild'] as List<String>,
                [] as List<String>,
                ['/contract/fixtures-cross-package-package-private-excluded'],
                'com.example.registry.LogContextRegistry')
        ContractProjectHarness.runCapture(crossPackageProjectDir)

        when:
        def rendered = ContractResultReader.readToString(crossPackageProjectDir, 'CrossPackagePrivateChild')

        then:
        ContractResultReader.readLoggable(crossPackageProjectDir, 'CrossPackagePrivateChild')

        rendered.startsWith('CrossPackagePrivateChild(')
        rendered.endsWith(')')

        rendered.contains('childOwnField=child-own-val')
        rendered.contains('publicParentField=public-parent-val')
        rendered.contains('protectedParentField=protected-parent-val')

        !rendered.contains('packagePrivateParentField=')

        cleanup:
        crossPackageProjectDir?.deleteDir()
    }

    def "toString contract: synthetic outer-class reference (this\$0) of an inner class is excluded from toString"() {
        given:
        ensureShadowFieldRenderCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(shadowFieldRenderProjectDir, 'OuterWithInnerDto')

        then:
        ContractResultReader.readLoggable(shadowFieldRenderProjectDir, 'OuterWithInnerDto')

        rendered.startsWith('OuterWithInnerDto(')
        rendered.endsWith(')')

        rendered.contains('outerField=outer-val')
        rendered.contains('innerField=inner-val')

        !rendered.contains('this$0')
    }

    def "escape hatch: @AutomatedProcessingIgnore on a class produces no toString rewrite and no ENTER/EXIT"() {
        given:
        ensureAutomatedEscapeHatchCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(automatedEscapeHatchProjectDir, 'AutomatedProcessingIgnoreClassSubject')
        def captured = ContractResultReader.readCallsite(automatedEscapeHatchProjectDir, 'AutomatedProcessingIgnoreClassSubject')

        then:
        rendered.startsWith('contract.AutomatedProcessingIgnoreClassSubject@')
        !rendered.contains('secret=')
        !rendered.contains('(')

        and:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY runCall=== ',
                'INFO  boundary - ===BOUNDARY END=== ',
        ]
    }

    def "escape hatch: @AutomatedProcessingIgnore on a public instance method skips ENTER/EXIT while sibling methods are still instrumented"() {
        given:
        ensureAutomatedEscapeHatchCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(automatedEscapeHatchProjectDir, 'AutomatedProcessingIgnoreMethodSubject')

        then:
        def lines = captured.readLines()

        lines.contains('INFO  boundary - ===BOUNDARY ignoredCall=== ')
        lines.contains('INFO  boundary - ===BOUNDARY trackedCall=== ')

        !lines.any { it.contains('|> [ENTER] AutomatedProcessingIgnoreMethodSubject.ignored') }
        !lines.any { it.contains('|< [EXIT] AutomatedProcessingIgnoreMethodSubject.ignored') }

        lines.any { it.contains('|> [ENTER] AutomatedProcessingIgnoreMethodSubject.tracked(x=arg-tracked)') }
        lines.any { it.contains('|< [EXIT] AutomatedProcessingIgnoreMethodSubject.tracked(value=arg-tracked-tracked-result)') }
    }

    def "escape hatch: @AutomatedProcessingIgnore on a parent does not propagate to the subclass"() {
        given:
        ensureAutomatedEscapeHatchCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(automatedEscapeHatchProjectDir, 'AutomatedProcessingIgnoreNotInheritedChild')
        def captured = ContractResultReader.readCallsite(automatedEscapeHatchProjectDir, 'AutomatedProcessingIgnoreNotInheritedChild')

        then:
        rendered.startsWith('AutomatedProcessingIgnoreNotInheritedChild(')
        rendered.endsWith(')')
        rendered.contains('childField=child-val')

        and:
        def lines = captured.readLines()
        lines.any { it.contains('|> [ENTER] AutomatedProcessingIgnoreNotInheritedChild.childMethod(x=arg-child)') }
        lines.any { it.contains('|< [EXIT] AutomatedProcessingIgnoreNotInheritedChild.childMethod(value=arg-child-child-result)') }
    }

    def "method logging field: extractor return value (string / null / primitive) is appended to the ENTER line as fieldName=value"() {
        given:
        ensureMethodLoggingFieldExtractorsCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(methodLoggingFieldExtractorsProjectDir, subject)

        then:
        def enterLine = Objects.requireNonNull(
                captured.readLines().find { it.contains("|> [ENTER] ${subject}.run") },
                "expected ENTER line for ${subject}.run")
        enterLine.contains('x=arg-run')
        enterLine.contains("${fieldName}=${fieldValue}")

        and:
        captured.contains("|< [EXIT] ${subject}.run(value=arg-run-result)")

        where:
        subject                          | fieldName   | fieldValue
        'ExtractorStringValueService'    | 'traceId'   | 'trace-xxx'
        'ExtractorNullValueService'      | 'nullField' | 'null'
        'ExtractorPrimitiveValueService' | 'counter'   | '42'
    }

    def "method logging field: extractor that throws is reported via the logging failure path and does not break the ENTER line"() {
        given:
        ensureMethodLoggingFieldExtractorsCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(methodLoggingFieldExtractorsProjectDir, 'ExtractorThrowingService')

        then:
        captured.contains('libprunus logging failure at')
        captured.contains('extractor-bang')

        and:
        captured.contains('===BOUNDARY runCall===')
        captured.contains('===BOUNDARY END===')
    }

    def "method logging field: a registry-declared @MethodLoggingField not referenced by the matching profile does not appear in the ENTER line"() {
        given:
        ensureUnreferencedFieldsCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(unreferencedFieldsProjectDir, 'UnreferencedFieldService')

        then:
        def enterLine = Objects.requireNonNull(
                captured.readLines().find { it.contains('|> [ENTER] UnreferencedFieldService.run') },
                'expected ENTER line for UnreferencedFieldService.run')
        enterLine.contains('traceId=trace-xxx')

        !enterLine.contains('unused=')
        !captured.contains('unused=unused-value')
    }

    def "method logging contract: entryLevel OFF suppresses ENTER while exitLevel INFO still emits EXIT"() {
        given:
        def entryOffProjectDir = java.nio.file.Files.createTempDirectory('entry-level-off-').toFile()
        ContractProjectHarness.writeBaseProjectWithFixturesAndCustomLogback(
                entryOffProjectDir,
                [] as List<String>,
                ['contract.EntryLevelOffService'] as List<String>,
                ['/contract/fixtures-entry-level-off'],
                'contract.LogContextRegistry',
                stdLogbackXml('INFO'))
        ContractProjectHarness.runCapture(entryOffProjectDir)

        when:
        def captured = ContractResultReader.readCallsite(entryOffProjectDir, 'EntryLevelOffService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY runCall===',
                'INFO  EntryLevelOffService - |< [EXIT] EntryLevelOffService.run(value=arg-run-result)',
                'INFO  boundary - ===BOUNDARY END===',
        ]

        cleanup:
        entryOffProjectDir?.deleteDir()
    }

    def "method logging contract: exitLevel OFF suppresses EXIT while entryLevel INFO still emits ENTER"() {
        given:
        def exitOffProjectDir = java.nio.file.Files.createTempDirectory('exit-level-off-').toFile()
        ContractProjectHarness.writeBaseProjectWithFixturesAndCustomLogback(
                exitOffProjectDir,
                [] as List<String>,
                ['contract.ExitLevelOffService'] as List<String>,
                ['/contract/fixtures-exit-level-off'],
                'contract.LogContextRegistry',
                stdLogbackXml('INFO'))
        ContractProjectHarness.runCapture(exitOffProjectDir)

        when:
        def captured = ContractResultReader.readCallsite(exitOffProjectDir, 'ExitLevelOffService')

        then:
        captured.readLines() == [
                'INFO  boundary - ===BOUNDARY runCall===',
                'INFO  ExitLevelOffService - |> [ENTER] ExitLevelOffService.run(x=arg-run)',
                'INFO  boundary - ===BOUNDARY END===',
        ]

        cleanup:
        exitOffProjectDir?.deleteDir()
    }

    @Shared
    @TempDir
    File typeDoNotLogMatrixProjectDir

    private static final List<String> TYPE_DONOTLOG_DTO_FQCNS = [] as List<String>

    private static final List<String> TYPE_DONOTLOG_SERVICE_FQCNS = [
            'contract.TypeLevelDoNotLogPlainMethodSubject',
            'contract.TypeLevelDoNotLogMethodDoLogOverrideSubject',
            'contract.TypeLevelDoNotLogMethodSensitiveOverrideSubject',
            'contract.TypeLevelDoNotLogMethodDoLogParamDoNotLogSubject',
            'contract.TypeLevelDoNotLogMixedParamsSubject',
            'contract.TypeLevelDoNotLogParamDoLogOverrideSubject',
            'contract.TypeLevelDoNotLogParamSensitiveOverrideSubject',
            'contract.TypeLevelDoNotLogAncestorDoLogSuppressedBySubclassSubject',
            'contract.TypeLevelDoNotLogSuperclassSuppressInheritorSubject',
            'contract.TypeLevelDoNotLogInterfaceSuppressImplementorSubject',
    ]

    private void ensureTypeDoNotLogMatrixCaptureReady() {
        if (new File(typeDoNotLogMatrixProjectDir, 'build/contract-results').isDirectory()) {
            return
        }
        ContractProjectHarness.writeBaseProjectWithFixturesAndCustomLogback(
                typeDoNotLogMatrixProjectDir,
                TYPE_DONOTLOG_DTO_FQCNS,
                TYPE_DONOTLOG_SERVICE_FQCNS,
                ['/contract/fixtures-type-donotlog-matrix'],
                'contract.LogContextRegistry',
                KVP_SUPPRESSED_LOGBACK_XML)
        ContractProjectHarness.runCapture(typeDoNotLogMatrixProjectDir)
    }

    @Shared
    @TempDir
    File methodEligibilityFamilyMatrixProjectDir

    private static final List<String> METHOD_ELIGIBILITY_FAMILY_MATRIX_SERVICE_FQCNS = [
            'contract.EligibilityStaticMethodWithSensitiveParamSubject',
            'contract.EligibilityStaticFactoryWithSensitiveParamSubject',
            'contract.EligibilityStaticMethodWithMethodLevelDoNotLogSubject',
            'contract.EligibilityConstructorWithDoLogParamSubject',
            'contract.EligibilityPrivateMethodWithSensitiveParamSubject',
            'contract.EligibilityPackagePrivateMethodWithSensitiveParamSubject',
            'contract.EligibilityProtectedMethodWithDoNotLogParamSubject',
            'contract.EligibilitySyntheticLambdaWithSensitiveParamSubject',
            'contract.EligibilityObjectEqualsOverrideWithSensitiveParamSubject',
            'contract.EligibilityObjectHashCodeOverrideWithDoNotLogSubject',
            'contract.EligibilityObjectToStringOverrideWithDoLogSubject',
            'contract.EligibilityConcreteImplOfAbstractSensitiveParamSubject',
    ]

    private void ensureMethodEligibilityFamilyMatrixCaptureReady() {
        if (new File(methodEligibilityFamilyMatrixProjectDir, 'build/contract-results').isDirectory()) {
            return
        }
        ContractProjectHarness.writeBaseProjectWithFixturesAndCustomLogback(
                methodEligibilityFamilyMatrixProjectDir,
                [] as List<String>,
                METHOD_ELIGIBILITY_FAMILY_MATRIX_SERVICE_FQCNS,
                ['/contract/fixtures-method-eligibility-family-matrix'],
                'contract.LogContextRegistry',
                KVP_SUPPRESSED_LOGBACK_XML)
        ContractProjectHarness.runCapture(methodEligibilityFamilyMatrixProjectDir)
    }

    @Shared
    @TempDir
    File pojoVisibilityProjectDir

    private static final List<String> POJO_VISIBILITY_DTO_FQCNS = [
            'org.libprunus.core.plugin.aot.log.fixture.pojovisibility.crosspkg.matched.CrossPkgPublicFieldMatchedSubject',
            'org.libprunus.core.plugin.aot.log.fixture.pojovisibility.crosspkg.matched.CrossPkgProtectedFieldMatchedSubject',
            'org.libprunus.core.plugin.aot.log.fixture.pojovisibility.crosspkg.matched.CrossPkgPackagePrivateFieldMatchedSubject',
            'org.libprunus.core.plugin.aot.log.fixture.pojovisibility.samepkg.SamePkgPackagePrivateMatchedSubject',
            'org.libprunus.core.plugin.aot.log.fixture.pojovisibility.crosspkg.matched.CrossPkgSensitiveFieldMatchedSubject',
            'org.libprunus.core.plugin.aot.log.fixture.pojovisibility.crosspkg.matched.CrossPkgDoNotLogFieldMatchedSubject',
            'org.libprunus.core.plugin.aot.log.fixture.pojovisibility.crosspkg.matched.CrossPkgTypeSensitiveOuterMatchedSubject',
            'org.libprunus.core.plugin.aot.log.fixture.pojovisibility.crosspkg.matched.CrossPkgIfaceImplMatchedSubject',
    ]

    private void ensurePojoVisibilityCaptureReady() {
        if (new File(pojoVisibilityProjectDir, 'build/contract-results').isDirectory()) {
            return
        }
        ContractProjectHarness.writeBaseProjectWithMultiPackageFixtures(
                pojoVisibilityProjectDir,
                POJO_VISIBILITY_DTO_FQCNS,
                [] as List<String>,
                ['/contract/fixtures-pojovisibility-profile-mismatch'],
                'org.libprunus.core.plugin.aot.log.fixture.pojovisibility.registry.LogContextRegistry')
        ContractProjectHarness.runCapture(pojoVisibilityProjectDir)
    }

    def "escape hatch: class-level @AutomatedProcessingIgnore with field-level family annotations leaves all fields unrewritten and renders Object.toString"() {
        given:
        ensureAutomatedEscapeHatchCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(automatedEscapeHatchProjectDir, 'AutomatedTypeIgnoreSensitiveFieldsSubject')

        then:
        rendered.startsWith('contract.AutomatedTypeIgnoreSensitiveFieldsSubject@')
        !rendered.contains('maskedField=')
        !rendered.contains('suppressedField=')
        !rendered.contains('passThroughField=')
        !rendered.contains('plainField=')
    }

    def "escape hatch: class-level @AutomatedProcessingIgnore with method-level @DoNotLog and parameter-level @Sensitive emits no ENTER/EXIT"() {
        given:
        ensureAutomatedEscapeHatchCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(automatedEscapeHatchProjectDir, 'AutomatedTypeIgnoreSensitiveParamSubject')

        then:
        def lines = captured.readLines()

        lines.contains('INFO  boundary - ===BOUNDARY processCall=== ')
        lines.contains('INFO  boundary - ===BOUNDARY END=== ')

        !lines.any { it.contains('|> [ENTER] AutomatedTypeIgnoreSensitiveParamSubject') }
        !lines.any { it.contains('|< [EXIT] AutomatedTypeIgnoreSensitiveParamSubject') }
    }

    def "escape hatch: method-level @AutomatedProcessingIgnore overrides type-level @DoNotLog for the ignored method; sibling method follows the type-level resolution"() {
        given:
        ensureAutomatedEscapeHatchCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(automatedEscapeHatchProjectDir, 'AutomatedMethodIgnoreOverridesClassDoNotLogSubject')

        then:
        def lines = captured.readLines()

        lines.contains('INFO  boundary - ===BOUNDARY ignoredCall=== ')
        lines.contains('INFO  boundary - ===BOUNDARY trackedCall=== ')

        !lines.any { it.contains('|> [ENTER] AutomatedMethodIgnoreOverridesClassDoNotLogSubject.ignored') }
        !lines.any { it.contains('|< [EXIT] AutomatedMethodIgnoreOverridesClassDoNotLogSubject.ignored') }

        and:
        !lines.any { it.contains('|> [ENTER] AutomatedMethodIgnoreOverridesClassDoNotLogSubject.tracked') }
        !lines.any { it.contains('|< [EXIT] AutomatedMethodIgnoreOverridesClassDoNotLogSubject.tracked') }
    }

    def "escape hatch: method-level @AutomatedProcessingIgnore overrides type-level @Sensitive for the ignored method; sibling still masks"() {
        given:
        ensureAutomatedEscapeHatchCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(automatedEscapeHatchProjectDir, 'AutomatedMethodIgnoreOverridesClassSensitiveSubject')

        then:
        def lines = captured.readLines()

        lines.contains('INFO  boundary - ===BOUNDARY ignoredCall=== ')
        lines.contains('INFO  boundary - ===BOUNDARY trackedCall=== ')

        !lines.any { it.contains('|> [ENTER] AutomatedMethodIgnoreOverridesClassSensitiveSubject.ignored') }
        !lines.any { it.contains('|< [EXIT] AutomatedMethodIgnoreOverridesClassSensitiveSubject.ignored') }

        lines.any { it.contains('|> [ENTER] AutomatedMethodIgnoreOverridesClassSensitiveSubject.tracked(x=***)') }
        lines.any { it.contains('|< [EXIT] AutomatedMethodIgnoreOverridesClassSensitiveSubject.tracked(value=***)') }
    }

    def "escape hatch: method-level @AutomatedProcessingIgnore does not propagate to the enclosing class toString rewrite; sibling field still masks under type-level @Sensitive"() {
        given:
        ensureAutomatedEscapeHatchCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(automatedEscapeHatchProjectDir, 'AutomatedMethodIgnoreSiblingFieldsRemainSensitiveSubject')
        def captured = ContractResultReader.readCallsite(automatedEscapeHatchProjectDir, 'AutomatedMethodIgnoreSiblingFieldsRemainSensitiveSubject')

        then:
        rendered.startsWith('AutomatedMethodIgnoreSiblingFieldsRemainSensitiveSubject(')
        rendered.endsWith(')')
        rendered.contains('fieldMasked=***')
        !rendered.contains('fieldMasked=secret-val')

        and:
        def lines = captured.readLines()
        lines.contains('INFO  boundary - ===BOUNDARY ignoredMethodCall=== ')

        !lines.any { it.contains('|> [ENTER] AutomatedMethodIgnoreSiblingFieldsRemainSensitiveSubject.ignoredMethod') }
        !lines.any { it.contains('|< [EXIT] AutomatedMethodIgnoreSiblingFieldsRemainSensitiveSubject.ignoredMethod') }
    }

    def "escape hatch: method-level @AutomatedProcessingIgnore wins over parameter-level family annotations on the same signature; sibling without the hatch is instrumented"() {
        given:
        ensureAutomatedEscapeHatchCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(automatedEscapeHatchProjectDir, 'AutomatedMethodIgnoreWithSensitiveParamSubject')

        then:
        def lines = captured.readLines()

        lines.contains('INFO  boundary - ===BOUNDARY ignoredCall=== ')
        lines.contains('INFO  boundary - ===BOUNDARY trackedCall=== ')

        !lines.any { it.contains('|> [ENTER] AutomatedMethodIgnoreWithSensitiveParamSubject.ignored') }
        !lines.any { it.contains('|< [EXIT] AutomatedMethodIgnoreWithSensitiveParamSubject.ignored') }

        lines.any { it.contains('|> [ENTER] AutomatedMethodIgnoreWithSensitiveParamSubject.tracked(secret=***, plain=arg-plain)') }
        lines.any { it.contains('|< [EXIT] AutomatedMethodIgnoreWithSensitiveParamSubject.tracked(value=arg-secret/arg-plain)') }
    }

    def "escape hatch: @AutomatedProcessingIgnore on a private method is redundant; sibling public method is instrumented normally"() {
        given:
        ensureAutomatedEscapeHatchCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(automatedEscapeHatchProjectDir, 'AutomatedPrivateMethodIgnoreSubject')

        then:
        def lines = captured.readLines()

        lines.contains('INFO  boundary - ===BOUNDARY trackedPublicCall=== ')

        lines.any { it.contains('|> [ENTER] AutomatedPrivateMethodIgnoreSubject.trackedPublic(x=arg-tracked)') }
        lines.any { it.contains('|< [EXIT] AutomatedPrivateMethodIgnoreSubject.trackedPublic(value=arg-tracked-tracked-result)') }

        and:
        !lines.any { it.contains('|> [ENTER] AutomatedPrivateMethodIgnoreSubject.ignoredPrivate') }
        !lines.any { it.contains('|< [EXIT] AutomatedPrivateMethodIgnoreSubject.ignoredPrivate') }
    }

    def "escape hatch: @AutomatedProcessingIgnore on a public static method is redundant; sibling public instance method is instrumented normally"() {
        given:
        ensureAutomatedEscapeHatchCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(automatedEscapeHatchProjectDir, 'AutomatedStaticMethodIgnoreSubject')

        then:
        def lines = captured.readLines()

        lines.contains('INFO  boundary - ===BOUNDARY ignoredStaticCall=== ')
        lines.contains('INFO  boundary - ===BOUNDARY trackedInstanceCall=== ')

        !lines.any { it.contains('|> [ENTER] AutomatedStaticMethodIgnoreSubject.ignoredStatic') }
        !lines.any { it.contains('|< [EXIT] AutomatedStaticMethodIgnoreSubject.ignoredStatic') }

        lines.any { it.contains('|> [ENTER] AutomatedStaticMethodIgnoreSubject.trackedInstance(x=arg-tracked)') }
        lines.any { it.contains('|< [EXIT] AutomatedStaticMethodIgnoreSubject.trackedInstance(value=arg-tracked-tracked-result)') }
    }

    def "escape hatch: method-level @AutomatedProcessingIgnore on an override short-circuits family resolution before reaching interface type-level @DoNotLog"() {
        given:
        ensureAutomatedEscapeHatchCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(automatedEscapeHatchProjectDir, 'AutomatedMethodIgnoreOverridesIfaceDoNotLogSubject')

        then:
        def lines = captured.readLines()

        lines.contains('INFO  boundary - ===BOUNDARY runCall=== ')
        lines.contains('INFO  boundary - ===BOUNDARY trackedCall=== ')

        !lines.any { it.contains('|> [ENTER] AutomatedMethodIgnoreOverridesIfaceDoNotLogSubject.run') }
        !lines.any { it.contains('|< [EXIT] AutomatedMethodIgnoreOverridesIfaceDoNotLogSubject.run') }
    }

    def "type-level @DoNotLog: plain method with no closer family annotation triggers whole-method skip"() {
        given:
        ensureTypeDoNotLogMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(typeDoNotLogMatrixProjectDir, 'TypeLevelDoNotLogPlainMethodSubject')

        then:
        def lines = captured.readLines()
        lines.contains('INFO  boundary - ===BOUNDARY processCall=== ')
        lines.contains('INFO  boundary - ===BOUNDARY END=== ')

        !lines.any { it.contains('|> [ENTER] TypeLevelDoNotLogPlainMethodSubject') }
        !lines.any { it.contains('|< [EXIT] TypeLevelDoNotLogPlainMethodSubject') }
    }

    def "type-level @DoNotLog: method-level @DoLog override on same layer terminates resolution; method is instrumented plainly"() {
        given:
        ensureTypeDoNotLogMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(typeDoNotLogMatrixProjectDir, 'TypeLevelDoNotLogMethodDoLogOverrideSubject')

        then:
        def lines = captured.readLines()
        lines.any { it.contains('|> [ENTER] TypeLevelDoNotLogMethodDoLogOverrideSubject.describe(visible=arg-visible)') }
        lines.any { it.contains('|< [EXIT] TypeLevelDoNotLogMethodDoLogOverrideSubject.describe(value=arg-visible)') }
    }

    def "type-level @DoNotLog: method-level @Sensitive override on same layer terminates resolution; method is instrumented with mask"() {
        given:
        ensureTypeDoNotLogMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(typeDoNotLogMatrixProjectDir, 'TypeLevelDoNotLogMethodSensitiveOverrideSubject')

        then:
        def lines = captured.readLines()
        lines.any { it.contains('|> [ENTER] TypeLevelDoNotLogMethodSensitiveOverrideSubject.describe(masked=***)') }
        lines.any { it.contains('|< [EXIT] TypeLevelDoNotLogMethodSensitiveOverrideSubject.describe(value=***)') }
    }

    def "type-level @DoNotLog: method-level @DoLog override AND parameter-level @DoNotLog disagree per-target; both targets resolved independently"() {
        given:
        ensureTypeDoNotLogMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(typeDoNotLogMatrixProjectDir, 'TypeLevelDoNotLogMethodDoLogParamDoNotLogSubject')

        then:
        def enterLine = Objects.requireNonNull(
                captured.readLines().find { it.contains('|> [ENTER] TypeLevelDoNotLogMethodDoLogParamDoNotLogSubject.describe') },
                'expected ENTER line for TypeLevelDoNotLogMethodDoLogParamDoNotLogSubject.describe')
        enterLine.contains('visible=arg-visible')
        !enterLine.contains('dropped=')

        captured.contains('|< [EXIT] TypeLevelDoNotLogMethodDoLogParamDoNotLogSubject.describe(value=arg-visible)')
    }

    def "type-level @DoNotLog: mixed parameters resolve independently per closeness; ENTER line is not empty when any parameter carries family annotation"() {
        given:
        ensureTypeDoNotLogMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(typeDoNotLogMatrixProjectDir, 'TypeLevelDoNotLogMixedParamsSubject')

        then:
        def enterLine = Objects.requireNonNull(
                captured.readLines().find { it.contains('|> [ENTER] TypeLevelDoNotLogMixedParamsSubject.describe') },
                'expected ENTER line for TypeLevelDoNotLogMixedParamsSubject.describe')
        !enterLine.contains('plain=')
        enterLine.contains('masked=***')
        enterLine.contains('visible=arg-visible')
        !enterLine.contains('suppressed=')

        and:
        captured.contains('|< [EXIT] TypeLevelDoNotLogMixedParamsSubject.describe()')
    }

    def "type-level @DoNotLog: parameter-level @DoLog wins per closeness; return target still drops; method is instrumented"() {
        given:
        ensureTypeDoNotLogMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(typeDoNotLogMatrixProjectDir, 'TypeLevelDoNotLogParamDoLogOverrideSubject')

        then:
        captured.contains('|> [ENTER] TypeLevelDoNotLogParamDoLogOverrideSubject.describe(visible=arg-visible)')
        captured.contains('|< [EXIT] TypeLevelDoNotLogParamDoLogOverrideSubject.describe()')
    }

    def "type-level @DoNotLog: parameter-level @Sensitive disables whole-method skip; param masks and return drops"() {
        given:
        ensureTypeDoNotLogMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(typeDoNotLogMatrixProjectDir, 'TypeLevelDoNotLogParamSensitiveOverrideSubject')

        then:
        captured.contains('|> [ENTER] TypeLevelDoNotLogParamSensitiveOverrideSubject.describe(masked=***)')
        captured.contains('|< [EXIT] TypeLevelDoNotLogParamSensitiveOverrideSubject.describe()')
    }

    def "type-level @DoNotLog: layer-1 type-level annotation terminates the walk before any ancestor @DoLog is consulted"() {
        given:
        ensureTypeDoNotLogMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(typeDoNotLogMatrixProjectDir, 'TypeLevelDoNotLogAncestorDoLogSuppressedBySubclassSubject')

        then:
        def lines = captured.readLines()
        lines.contains('INFO  boundary - ===BOUNDARY describeCall=== ')

        !lines.any { it.contains('|> [ENTER] TypeLevelDoNotLogAncestorDoLogSuppressedBySubclassSubject') }
        !lines.any { it.contains('|< [EXIT] TypeLevelDoNotLogAncestorDoLogSuppressedBySubclassSubject') }
    }

    def "type-level @DoNotLog: layer-2 superclass type-level @DoNotLog reaches subclass plain method via upward walk"() {
        given:
        ensureTypeDoNotLogMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(typeDoNotLogMatrixProjectDir, 'TypeLevelDoNotLogSuperclassSuppressInheritorSubject')

        then:
        def lines = captured.readLines()
        lines.contains('INFO  boundary - ===BOUNDARY describeCall=== ')

        lines.contains('INFO  TypeLevelDoNotLogSuperclassSuppressInheritorSubject - |> [ENTER] TypeLevelDoNotLogSuperclassSuppressInheritorSubject.describe(input=arg-input) ')
        lines.contains('INFO  TypeLevelDoNotLogSuperclassSuppressInheritorSubject - |< [EXIT] TypeLevelDoNotLogSuperclassSuppressInheritorSubject.describe(value=arg-input) ')
    }

    def "type-level @DoNotLog: layer-2 interface type-level @DoNotLog reaches implementer plain method via upward walk"() {
        given:
        ensureTypeDoNotLogMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(typeDoNotLogMatrixProjectDir, 'TypeLevelDoNotLogInterfaceSuppressImplementorSubject')

        then:
        def lines = captured.readLines()
        lines.contains('INFO  boundary - ===BOUNDARY describeCall=== ')

        lines.contains('INFO  TypeLevelDoNotLogInterfaceSuppressImplementorSubject - |> [ENTER] TypeLevelDoNotLogInterfaceSuppressImplementorSubject.describe(input=arg-input) ')
        lines.contains('INFO  TypeLevelDoNotLogInterfaceSuppressImplementorSubject - |< [EXIT] TypeLevelDoNotLogInterfaceSuppressImplementorSubject.describe(value=arg-input) ')
    }

    def "method eligibility: ineligible method shape (static / constructor / non-public / synthetic / Object override) emits no ENTER/EXIT"() {
        given:
        ensureMethodEligibilityFamilyMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(methodEligibilityFamilyMatrixProjectDir, subject)

        then:
        !captured.contains("[ENTER] ${subject}${methodSuffix}")
        !captured.contains("[EXIT] ${subject}${methodSuffix}")

        where:
        subject                                                    | methodSuffix
        'EligibilityStaticMethodWithSensitiveParamSubject'         | ''
        'EligibilityStaticFactoryWithSensitiveParamSubject'        | ''
        'EligibilityStaticMethodWithMethodLevelDoNotLogSubject'    | ''
        'EligibilityConstructorWithDoLogParamSubject'              | ''
        'EligibilityPackagePrivateMethodWithSensitiveParamSubject' | '.packagePrivateMethod'
        'EligibilityProtectedMethodWithDoNotLogParamSubject'       | '.protectedMethod'
        'EligibilitySyntheticLambdaWithSensitiveParamSubject'      | '.lambda$'
        'EligibilityObjectEqualsOverrideWithSensitiveParamSubject' | '.equals'
        'EligibilityObjectHashCodeOverrideWithDoNotLogSubject'     | '.hashCode'
        'EligibilityObjectToStringOverrideWithDoLogSubject'        | '.toString'
    }

    def "method eligibility: private method with parameter-level @Sensitive emits no ENTER/EXIT; public driver is instrumented normally"() {
        given:
        ensureMethodEligibilityFamilyMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(methodEligibilityFamilyMatrixProjectDir, 'EligibilityPrivateMethodWithSensitiveParamSubject')

        then:
        !captured.contains('[ENTER] EligibilityPrivateMethodWithSensitiveParamSubject.privateMethod')
        !captured.contains('[EXIT] EligibilityPrivateMethodWithSensitiveParamSubject.privateMethod')

        and:
        captured.contains('|> [ENTER] EligibilityPrivateMethodWithSensitiveParamSubject.drivePrivate(value=arg-secret)')
        captured.contains('|< [EXIT] EligibilityPrivateMethodWithSensitiveParamSubject.drivePrivate(value=arg-secret)')
    }

    def "method eligibility: concrete override of abstract method inherits parameter-level @Sensitive from the ineligible abstract declaration"() {
        given:
        ensureMethodEligibilityFamilyMatrixCaptureReady()

        when:
        def captured = ContractResultReader.readCallsite(methodEligibilityFamilyMatrixProjectDir, 'EligibilityConcreteImplOfAbstractSensitiveParamSubject')

        then:
        captured.contains('|> [ENTER] EligibilityConcreteImplOfAbstractSensitiveParamSubject.handle(secret=***)')
        captured.contains('|< [EXIT] EligibilityConcreteImplOfAbstractSensitiveParamSubject.handle(value=arg-secret)')
    }

    def "pojo visibility: cross-package public field on unmatched outer renders verbatim through matched child"() {
        given:
        ensurePojoVisibilityCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(pojoVisibilityProjectDir, 'CrossPkgPublicFieldMatchedSubject')

        then:
        ContractResultReader.readLoggable(pojoVisibilityProjectDir, 'CrossPkgPublicFieldMatchedSubject')

        rendered.startsWith('CrossPkgPublicFieldMatchedSubject(')
        rendered.endsWith(')')
        rendered.contains('childOwnField=child-own-val')
        rendered.contains('publicOuterField=public-outer-val')
    }

    def "pojo visibility: cross-package protected field on unmatched outer renders verbatim through matched child"() {
        given:
        ensurePojoVisibilityCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(pojoVisibilityProjectDir, 'CrossPkgProtectedFieldMatchedSubject')

        then:
        ContractResultReader.readLoggable(pojoVisibilityProjectDir, 'CrossPkgProtectedFieldMatchedSubject')

        rendered.startsWith('CrossPkgProtectedFieldMatchedSubject(')
        rendered.endsWith(')')
        rendered.contains('childOwnField=child-own-val')
        rendered.contains('protectedOuterField=protected-outer-val')
    }

    def "pojo visibility: cross-package package-private field is excluded from matched child toString"() {
        given:
        ensurePojoVisibilityCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(pojoVisibilityProjectDir, 'CrossPkgPackagePrivateFieldMatchedSubject')

        then:
        ContractResultReader.readLoggable(pojoVisibilityProjectDir, 'CrossPkgPackagePrivateFieldMatchedSubject')

        rendered.startsWith('CrossPkgPackagePrivateFieldMatchedSubject(')
        rendered.endsWith(')')
        rendered.contains('childOwnField=child-own-val')
        !rendered.contains('packagePrivateOuterField')
    }

    def "pojo visibility: same-package package-private field is included in matched child toString"() {
        given:
        ensurePojoVisibilityCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(pojoVisibilityProjectDir, 'SamePkgPackagePrivateMatchedSubject')

        then:
        ContractResultReader.readLoggable(pojoVisibilityProjectDir, 'SamePkgPackagePrivateMatchedSubject')

        rendered.startsWith('SamePkgPackagePrivateMatchedSubject(')
        rendered.endsWith(')')
        rendered.contains('childOwnField=child-own-val')
        rendered.contains('packagePrivateOuterField=package-private-outer-val')
    }

    def "pojo visibility: field-level @Sensitive on field declared on unmatched cross-package outer still masks through matched child"() {
        given:
        ensurePojoVisibilityCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(pojoVisibilityProjectDir, 'CrossPkgSensitiveFieldMatchedSubject')

        then:
        ContractResultReader.readLoggable(pojoVisibilityProjectDir, 'CrossPkgSensitiveFieldMatchedSubject')

        rendered.startsWith('CrossPkgSensitiveFieldMatchedSubject(')
        rendered.endsWith(')')
        rendered.contains('childOwnField=child-own-val')
        rendered.contains('sensitiveOuterField=***')
        !rendered.contains('sensitive-outer-val')
    }

    def "pojo visibility: field-level @DoNotLog on field declared on unmatched cross-package outer drops the slot through matched child"() {
        given:
        ensurePojoVisibilityCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(pojoVisibilityProjectDir, 'CrossPkgDoNotLogFieldMatchedSubject')

        then:
        ContractResultReader.readLoggable(pojoVisibilityProjectDir, 'CrossPkgDoNotLogFieldMatchedSubject')

        rendered.startsWith('CrossPkgDoNotLogFieldMatchedSubject(')
        rendered.endsWith(')')
        rendered.contains('childOwnField=child-own-val')
        !rendered.contains('doNotLogOuterField')
        !rendered.contains('donotlog-outer-val')
    }

    def "pojo visibility: type-level @Sensitive on unmatched cross-package outer reaches root-layer child field via layer-2 walk"() {
        given:
        ensurePojoVisibilityCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(pojoVisibilityProjectDir, 'CrossPkgTypeSensitiveOuterMatchedSubject')

        then:
        ContractResultReader.readLoggable(pojoVisibilityProjectDir, 'CrossPkgTypeSensitiveOuterMatchedSubject')

        rendered.startsWith('CrossPkgTypeSensitiveOuterMatchedSubject(')
        rendered.endsWith(')')
        rendered.contains('childOwnField=child-own-val')
        rendered.contains('publicOuterField=***')
        !rendered.contains('public-outer-val')
    }

    def "pojo visibility: unmatched cross-package interface with default method contributes no field-like slot to matched implementer toString"() {
        given:
        ensurePojoVisibilityCaptureReady()

        when:
        def rendered = ContractResultReader.readToString(pojoVisibilityProjectDir, 'CrossPkgIfaceImplMatchedSubject')

        then:
        ContractResultReader.readLoggable(pojoVisibilityProjectDir, 'CrossPkgIfaceImplMatchedSubject')

        rendered.startsWith('CrossPkgIfaceImplMatchedSubject(')
        rendered.endsWith(')')
        rendered.contains('childOwnField=child-own-val')
        !rendered.contains('defaultMethodValue')
        !rendered.contains('iface-default-val')
    }

    def "method logging contract: entryLevel DEBUG is emitted when logback root level is DEBUG and suppressed when INFO"() {
        given:
        def rootDebugProjectDir = java.nio.file.Files.createTempDirectory('entry-level-debug-root-debug-').toFile()
        ContractProjectHarness.writeBaseProjectWithFixturesAndCustomLogback(
                rootDebugProjectDir,
                [] as List<String>,
                ['contract.EntryLevelDebugService'] as List<String>,
                ['/contract/fixtures-entry-level-debug'],
                'contract.LogContextRegistry',
                stdLogbackXml('DEBUG'))
        ContractProjectHarness.runCapture(rootDebugProjectDir)
        def rootInfoProjectDir = java.nio.file.Files.createTempDirectory('entry-level-debug-root-info-').toFile()
        ContractProjectHarness.writeBaseProjectWithFixturesAndCustomLogback(
                rootInfoProjectDir,
                [] as List<String>,
                ['contract.EntryLevelDebugService'] as List<String>,
                ['/contract/fixtures-entry-level-debug'],
                'contract.LogContextRegistry',
                stdLogbackXml('INFO'))
        ContractProjectHarness.runCapture(rootInfoProjectDir)

        when:
        def rootDebugCaptured = ContractResultReader.readCallsite(rootDebugProjectDir, 'EntryLevelDebugService')
        def rootInfoCaptured = ContractResultReader.readCallsite(rootInfoProjectDir, 'EntryLevelDebugService')

        then:
        rootDebugCaptured.contains('DEBUG EntryLevelDebugService - |> [ENTER] EntryLevelDebugService.run(x=arg-run)')
        rootDebugCaptured.contains('INFO  EntryLevelDebugService - |< [EXIT] EntryLevelDebugService.run(value=arg-run-result)')

        and:
        !rootInfoCaptured.contains('[ENTER] EntryLevelDebugService')
        rootInfoCaptured.contains('INFO  EntryLevelDebugService - |< [EXIT] EntryLevelDebugService.run(value=arg-run-result)')

        cleanup:
        rootDebugProjectDir?.deleteDir()
        rootInfoProjectDir?.deleteDir()
    }
}
