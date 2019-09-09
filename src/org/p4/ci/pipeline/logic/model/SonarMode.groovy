package org.p4.ci.pipeline.logic.model

enum SonarMode {
    STRICT, PERMISSIVE, DISABLED,
    NO_QUALITY_CHECK    //TODO: Temporary as workaround for potential issues with Sonar integration

    boolean isEnabled() {
        return this != DISABLED
    }

    boolean isQualityGateEnabled() {
        return isEnabled() && this != NO_QUALITY_CHECK
    }
}