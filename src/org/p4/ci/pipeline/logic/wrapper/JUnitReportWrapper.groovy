package org.p4.ci.pipeline.logic.wrapper


import org.p4.ci.pipeline.logic.PipelineScriptAwareBlockWrapper

import static org.p4.ci.pipeline.util.PipelineScriptUtils.executeInScriptContext

class JUnitReportWrapper implements PipelineScriptAwareBlockWrapper {

    private final String reportsPath
    private final Script pipelineScript

    private JUnitReportWrapper(Script pipelineScript, String reportsPath) {
        this.pipelineScript = pipelineScript
        this.reportsPath = reportsPath
    }

    @Override
    def wrap(Closure codeBlock) {
        return executeInScriptContext(pipelineScript) {
            try {
                return codeBlock()
            } finally {
                junit reportsPath
            }
        }
    }

    static JUnitReportWrapper withJUnitForMavenReportPublished(Script pipelineScript) {
        return new JUnitReportWrapper(pipelineScript,"**/target/surefire-reports/*.xml")
    }

    static JUnitReportWrapper withJUnitForGradleReportPublished(Script pipelineScript) {
        return new JUnitReportWrapper(pipelineScript,"**/build/test-results/**/*.xml")
    }
}
