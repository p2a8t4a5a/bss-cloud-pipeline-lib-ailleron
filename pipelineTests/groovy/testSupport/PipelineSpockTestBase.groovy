//Based on https://github.com/macg33zr/pipelineUnit - licensed under the Apache License 2.0
package testSupport

import com.lesfurets.jenkins.unit.RegressionTest
import spock.lang.Specification

/**
 * A base class for Spock testing using the pipeline helper
 */
class PipelineSpockTestBase extends Specification implements RegressionTest {

    /**
     * Delegate to the test helper
     */
    @Delegate PipelineTestHelper pipelineTestHelper

    /**
     * Perform the common setup
     */
    def setup() {

        // Set callstacks path for RegressionTest
        callStackPath = 'pipelineTests/groovy/tests/callstacks/'

        // Create and config the helper
        pipelineTestHelper = new PipelineTestHelper()
        pipelineTestHelper.setUp()
    }
}
