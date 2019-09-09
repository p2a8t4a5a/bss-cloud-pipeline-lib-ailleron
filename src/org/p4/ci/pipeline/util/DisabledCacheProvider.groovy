package org.p4.ci.pipeline.util

class DisabledCacheProvider implements CacheProvider {

    private final Script pipelineScript

    DisabledCacheProvider(Script pipelineScript) {
        this.pipelineScript = pipelineScript
    }

    @Override
    void prepareCache() {
        pipelineScript.println "Build cache disabled"
    }

    @Override
    void summarizeCache() {
    }

    @Override
    String toString() {
        return "DisabledCacheProvider{}";
    }
}
