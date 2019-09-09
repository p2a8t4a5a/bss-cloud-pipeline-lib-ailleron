package org.p4.ci.pipeline.logic.builder

import org.p4.ci.pipeline.logic.model.BuildTool
import org.p4.ci.pipeline.logic.model.SonarMode
import org.p4.ci.pipeline.util.PipelineConfigurer

import static org.p4.ci.pipeline.util.PipelineScriptUtils.executeInScriptContext

abstract class AbstractJavaBuilder implements CodeBuilder {

    protected final Script pipelineScript
    protected final PipelineConfigurer pipelineConfigurer

    protected AbstractJavaBuilder(Script pipelineScript, PipelineConfigurer pipelineConfigurer) {
        this.pipelineScript = pipelineScript
        this.pipelineConfigurer = pipelineConfigurer
    }

    @Override
    void inBuildContext(BuildTool buildTool, Closure codeBlock) {
        executeInScriptContext {
            pipelineConfigurer.cacheWrapper.runWithBuildCacheContext {
                withCredentials([usernamePassword(credentialsId: "${env.NAMESPACE}-artifactory-credentials", usernameVariable: 'MAVEN_USERNAME',
                        passwordVariable: 'MAVEN_PASSWORD')]) {
                    buildTool.withTool(pipelineScript, codeBlock)
                }
            }
        }
    }

    @Override
    void prepareTestResources() {
        executeInScriptContext {
            testResourcesUtils.prepareTestResources(env.APP_NAME, env.NAMESPACE, env.PIPELINE_TEST_RESOURCES_BASE_NAME)
        }
    }

    @Override
    void cleanupTestResources() {
        executeInScriptContext {
            testResourcesUtils.cleanupTestResources(env.APP_NAME, env.NAMESPACE, env.PIPELINE_TEST_RESOURCES_BASE_NAME)
        }
    }

    @Override
    void waitForQualityGateResult(SonarMode mode) {
        executeInScriptContext {
            if (!mode.qualityGateEnabled) {
                echo "Quality Gate check disabled in configuration."
                return
            }

            def result = null
            //Required as failure/error on Sonar analysis (as distinct from "too many violations") break the pipeline anyway
            scriptUtils.mayFail {
                result = waitForQualityGate(abortPipeline: false)
            }

            echo "Quality gate result: ${result?.status}"
            if (result?.status != "OK") {
                String errorMessage = "Quality gate failure: ${result?.status}"
                if (mode == SonarMode.PERMISSIVE) {
                    echo "WARN. ${errorMessage}. Marking build as UNSTABLE"
                    currentBuild.result = "UNSTABLE"
                } else {
                    error "ERROR. ${errorMessage}"
                }
            }
        }
    }

    protected String getUploadRepoForVersion(String version) {
        return version.contains("SNAPSHOT") ? "libs-snapshot-local" : "libs-release-local"
    }

    protected def executeInScriptContext(Closure codeBlock) {
        return executeInScriptContext(pipelineScript, codeBlock)
    }
}
