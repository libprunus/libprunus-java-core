package org.libprunus.core.plugin.aot.log.fixture.inspect;

public class InspectBoundedGenericService implements InspectBoundedGenericPort<CharSequence> {

    @Override
    public CharSequence normalize(CharSequence input) {
        return input;
    }
}
