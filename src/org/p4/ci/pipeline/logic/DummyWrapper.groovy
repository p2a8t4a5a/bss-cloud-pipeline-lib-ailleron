package org.p4.ci.pipeline.logic

import org.p4.ci.pipeline.util.PipelineScriptUtils

class DummyWrapper implements BlockWrapper {

    @Override
    def wrap(Script pipelineScript, Closure codeBlock) {
        return PipelineScriptUtils.executeInScriptContext(pipelineScript, codeBlock)
    }
}
