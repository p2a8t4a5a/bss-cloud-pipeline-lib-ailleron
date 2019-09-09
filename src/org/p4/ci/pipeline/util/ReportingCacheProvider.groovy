package org.p4.ci.pipeline.util

class ReportingCacheProvider implements CacheProvider {

    private final Script pipelineScript

    ReportingCacheProvider(Script pipelineScript) {
        this.pipelineScript = pipelineScript
    }

    @Override
    void prepareCache() {
        pipelineScript.println "Build cache enabled"

        pipelineScript.sh '''
            df -h | grep /home/jenkins/.build-cache | { grep -v grep || test $? = 1; }
        '''
    }

    @Override
    void summarizeCache() {

        pipelineScript.println "Summarizing build cache"

        pipelineScript.sh '''
            ls -la /home/jenkins/.m2/repository
            df -h | grep /home/jenkins/.build-cache | { grep -v grep || test $? = 1; }
        '''
    }

    @Override
    public String toString() {
        return "ReportingCacheProvider{}";
    }
}
