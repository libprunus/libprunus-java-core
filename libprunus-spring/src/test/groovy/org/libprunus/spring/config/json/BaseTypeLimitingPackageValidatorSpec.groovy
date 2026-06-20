package org.libprunus.spring.config.json

import org.libprunus.spring.config.json.fixture.Animal
import org.libprunus.spring.config.json.fixture.Dog
import org.libprunus.spring.config.json.fixture.PolymorphicFixtures
import spock.lang.Specification
import tools.jackson.databind.jsontype.PolymorphicTypeValidator.Validity
import tools.jackson.databind.type.TypeFactory

class BaseTypeLimitingPackageValidatorSpec extends Specification {

    private final TypeFactory types = TypeFactory.createDefaultInstance()
    private final BaseTypeLimitingPackageValidator validator =
            new BaseTypeLimitingPackageValidator([PolymorphicFixtures.FIXTURE_PACKAGE])

    def "validateBaseType defers a safe base type to subtype checking instead of allowing or denying it"() {
        expect:
        validator.validateBaseType(null, types.constructType(Animal)) == Validity.INDETERMINATE
    }

    def "validateSubType withholds a verdict so that subtype gating stays on the class-name path"() {
        expect:
        validator.validateSubType(null, types.constructType(Animal), types.constructType(Dog)) == Validity.INDETERMINATE
    }
}
