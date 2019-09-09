package org.p4.ci.pipeline.logic.builder

import org.p4.ci.pipeline.logic.model.BuildTool
import org.p4.ci.pipeline.logic.model.SonarMode

interface CodeBuilder {

    //TODO: Move BuildTool (List<BuildTool>) to class constructor
    void inBuildContext(BuildTool buildTool, Closure codeBlock)

    void prepare()

    void prepareTestResources()

    void buildCode(boolean failOnVersionMisconfiguration)

    void testCode()

    void cleanupTestResources()

    void uploadStubs(def artifactoryServer)

    void executeSonar()

    void waitForQualityGateResult(SonarMode mode)
}
