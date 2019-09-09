import org.p4.ci.pipeline.PipelineConstants

def call(Closure body) {
    //TODO: Adjust OpenShift templates to leverage p4PipelineScripted directly
    p4PipelineScripted(PipelineConstants.PIPELINE_RELEASE_MODE, body)
}
