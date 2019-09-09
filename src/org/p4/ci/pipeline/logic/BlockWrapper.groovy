package org.p4.ci.pipeline.logic

interface BlockWrapper {

    def wrap(Script pipelineScript, Closure codeBlock)
}
