package org.p4.ci.pipeline.logic

interface PipelineScriptAwareBlockWrapper {

    def wrap(Closure codeBlock)
}