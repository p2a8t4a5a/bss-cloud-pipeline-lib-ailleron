package tests.job

import testSupport.PipelineSpockTestBase

class DummyPipelineSpec extends PipelineSpockTestBase {

    def "should not fail on dummy pipeline"() {
        when:
            runScript('pipelineTests/groovy/tests/job/testjob/DummyPipeline.groovy')
        then:
            printCallStack()
            assertJobStatusSuccess()
    }
}