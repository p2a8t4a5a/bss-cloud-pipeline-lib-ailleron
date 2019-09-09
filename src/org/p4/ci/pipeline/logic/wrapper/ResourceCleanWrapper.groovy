package org.p4.ci.pipeline.logic.wrapper

import static org.p4.ci.pipeline.util.PipelineScriptUtils.executeInScriptContext

class ResourceCleanWrapper {

    private final Script pipelineScript
    private final Closure codeBlockToCleanUpLater

    private ResourceCleanWrapper(Script pipelineScript, Closure codeBlockToCleanUpLater) {
        this.pipelineScript = pipelineScript
        this.codeBlockToCleanUpLater = codeBlockToCleanUpLater
    }

    def finalizedBy(Closure cleanupBlock) {
        return executeInScriptContext(pipelineScript) {
            try {
                return codeBlockToCleanUpLater()
            } finally {
                scriptUtils.mayFail {
                    echo "Cleaning up resources"
                    cleanupBlock()
                }
            }
        }
    }

    static ResourceCleanWrapper executeWithResourceCleaning(Script pipelineScript, Closure codeBlockToCleanUpLater) {
        return new ResourceCleanWrapper(pipelineScript, codeBlockToCleanUpLater)
    }
}
