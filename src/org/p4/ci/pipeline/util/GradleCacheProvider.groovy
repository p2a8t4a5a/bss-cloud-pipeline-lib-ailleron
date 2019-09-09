package org.p4.ci.pipeline.util

class GradleCacheProvider implements CacheProvider {

    private final Script pipelineScript

    GradleCacheProvider(Script pipelineScript) {
        this.pipelineScript = pipelineScript
    }

    @Override
    void prepareCache() {

        pipelineScript.println "Preparing Gradle build cache"

        if (pipelineScript.fileExists('/home/jenkins/.gradle/caches')) {
            pipelineScript.println "WARNING. Local Gradle repository directory already exists. Caching may not be kept across builds."
        }

        pipelineScript.sh """
            mkdir -p /home/jenkins/.build-cache/.gradle/caches
            mkdir -p /home/jenkins/.gradle
    
            ln -sfn /home/jenkins/.build-cache/.gradle/caches /home/jenkins/.gradle/caches
            ls -la /home/jenkins/.gradle
        """
    }

    @Override
    void summarizeCache() {
    }

    @Override
    String toString() {
        return "GradleCacheProvider{}";
    }
}
