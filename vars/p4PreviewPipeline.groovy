import org.p4.ci.pipeline.PipelineConstants

def call(body) {
    //TODO: Adjust OpenShift templates to leverage p4PipelineScripted directly
    p4PipelineScripted(PipelineConstants.PIPELINE_PREVIEW_MODE, body)
}
