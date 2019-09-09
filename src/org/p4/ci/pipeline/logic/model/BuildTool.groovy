package org.p4.ci.pipeline.logic.model

import org.p4.ci.pipeline.logic.BlockWrapper
import org.p4.ci.pipeline.logic.DummyWrapper
import org.p4.ci.pipeline.logic.JdkChangeWrapper

import static org.p4.ci.pipeline.PipelineConstants.DEFAULT_JAVA_11_TOOL_OPTIONS
import static org.p4.ci.pipeline.PipelineConstants.DEFAULT_JAVA_TOOL_OPTIONS

enum BuildTool {

    JDK8(new JdkChangeWrapper("jdk8", DEFAULT_JAVA_TOOL_OPTIONS)),
    OPENJDK11(new JdkChangeWrapper("openjdk11", DEFAULT_JAVA_11_TOOL_OPTIONS)),

    DUMMY(new DummyWrapper());

    private final BlockWrapper applicable

    BuildTool(BlockWrapper applicable) {
        this.applicable = applicable
    }

    def withTool(Script pipelineScript, Closure codeBlock) {
        return applicable.wrap(pipelineScript, codeBlock)
    }
}
