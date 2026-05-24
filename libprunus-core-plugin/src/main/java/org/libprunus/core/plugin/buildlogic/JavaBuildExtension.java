package org.libprunus.core.plugin.buildlogic;

import javax.inject.Inject;
import org.gradle.api.provider.Property;

public abstract class JavaBuildExtension {

    private static final int DEFAULT_JAVA_VERSION = 25;
    private static final double DEFAULT_COVERAGE_THRESHOLD = 0.9;

    @Inject
    public JavaBuildExtension() {
        getTargetJavaVersion().convention(DEFAULT_JAVA_VERSION);
        getInstructionCoverageThreshold().convention(DEFAULT_COVERAGE_THRESHOLD);
        getLineCoverageThreshold().convention(DEFAULT_COVERAGE_THRESHOLD);
        getBranchCoverageThreshold().convention(DEFAULT_COVERAGE_THRESHOLD);
    }

    public abstract Property<Integer> getTargetJavaVersion();

    public abstract Property<Double> getInstructionCoverageThreshold();

    public abstract Property<Double> getLineCoverageThreshold();

    public abstract Property<Double> getBranchCoverageThreshold();
}
