package org.p4.ci.pipeline.logic.builder

import org.p4.ci.pipeline.logic.model.BuildTool
import org.p4.ci.pipeline.logic.model.SonarMode

class UnsupportedCodeBuilder implements CodeBuilder {

    @Override
    void inBuildContext(BuildTool buildTool, Closure codeBlock) {
        codeBlock()
    }

    @Override
    void prepare() {
        //no-op
    }

    @Override
    void prepareTestResources() {

    }

    @Override
    void buildCode(boolean failOnVersionMisconfiguration) {
        throwUnsupportedException()
    }

    @Override
    void testCode() {
        throwUnsupportedException()
    }

    @Override
    void cleanupTestResources() {

    }

    @Override
    void uploadStubs(Object artifactoryServer) {
        throwUnsupportedException()
    }

    @Override
    void executeSonar() {
        throwUnsupportedException()
    }

    @Override
    void waitForQualityGateResult(SonarMode mode) {
        //no-op
    }

    private void throwUnsupportedException() {
        throw new UnsupportedOperationException("Provieded build configuration is not supported")
    }
}
