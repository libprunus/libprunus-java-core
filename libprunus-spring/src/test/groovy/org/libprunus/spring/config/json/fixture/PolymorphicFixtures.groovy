package org.libprunus.spring.config.json.fixture

import com.fasterxml.jackson.annotation.JsonTypeInfo
import tools.jackson.databind.json.JsonMapper

interface Animal {}

class Dog implements Animal {
    String name
}

class SafeBaseHolder {
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    Animal payload
}

class UnsafeBaseHolder {
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    Object payload
}

final class PolymorphicFixtures {

    static final String FIXTURE_PACKAGE = "org.libprunus.spring.config.json.fixture"

    private PolymorphicFixtures() {}

    static String safeBaseJson(String dogName) {
        JsonMapper.builder().build().writeValueAsString(new SafeBaseHolder(payload: new Dog(name: dogName)))
    }

    static String unsafeBaseJson(String dogName) {
        JsonMapper.builder().build().writeValueAsString(new UnsafeBaseHolder(payload: new Dog(name: dogName)))
    }
}
