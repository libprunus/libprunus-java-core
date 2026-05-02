package org.libprunus.core.plugin.aot.log;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class AotLogExtension {

    @Inject
    public AotLogExtension(ObjectFactory objectFactory) {
        getEnabled().convention(true);
        getTargetClassSuffixes().convention(List.of("Controller", "Service"));
        getPojoSuffixes().convention(List.of("Dto", "Request", "Response"));
        getClassNameFormat().convention(AotLogClassNameFormat.SIMPLE);
        getEnterLogLevel().convention(AotLogLevel.INFO);
        getExitLogLevel().convention(AotLogLevel.INFO);
        getHandleInaccessibleField().convention(false);
        getMaxToStringDepth().convention(5);
        getToStringWhitelist().convention(List.of());
    }

    public abstract Property<Boolean> getEnabled();

    public abstract ListProperty<String> getTargetClassSuffixes();

    public abstract ListProperty<String> getPojoSuffixes();

    public abstract Property<AotLogClassNameFormat> getClassNameFormat();

    public abstract Property<AotLogLevel> getEnterLogLevel();

    public abstract Property<AotLogLevel> getExitLogLevel();

    public abstract Property<Boolean> getHandleInaccessibleField();

    public abstract Property<Integer> getMaxToStringDepth();

    public abstract ListProperty<String> getToStringWhitelist();
}
