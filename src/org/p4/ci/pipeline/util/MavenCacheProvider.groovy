package org.p4.ci.pipeline.util

class MavenCacheProvider implements CacheProvider {

    private final Script pipelineScript

    MavenCacheProvider(Script pipelineScript) {
        this.pipelineScript = pipelineScript
    }

    @Override
    void prepareCache() {

        pipelineScript.println "Preparing Maven build cache"

        if (pipelineScript.fileExists('/home/jenkins/.m2/repository')) {
            pipelineScript.println "WARNING. Local Maven repository directory already exists. Caching may not be kept across builds."
        }

        pipelineScript.sh """
            mkdir -p /home/jenkins/.build-cache/.m2/repository
            mkdir -p /home/jenkins/.m2
    
            ln -sfn /home/jenkins/.build-cache/.m2/repository /home/jenkins/.m2/repository
            ls -la /home/jenkins/.m2
        """
    }

    @Override
    void summarizeCache() {
    }

    @Override
    String toString() {
        return "MavenCacheProvider{}";
    }
}
