package org.p4.ci.pipeline.util

import static org.p4.ci.pipeline.util.CacheWrapper.createNoOpCacheWrapper
import static org.p4.ci.pipeline.util.CacheWrapper.createStandardCacheWrapper

class PipelineConfigurer {

    final Script pipelineScript

    private CacheWrapper cacheWrapper

    PipelineConfigurer(Script pipelineScript) {
        this.pipelineScript = pipelineScript
    }

    //That fails with cryptic error if placed in constructor... - https://github.com/cloudbees/groovy-cps/pull/83/files
    PipelineConfigurer initialize(Map pipelineParams) {
        cacheWrapper = pipelineParams["buildCacheEnabled"] != false ? createStandardCacheWrapper(pipelineScript) : createNoOpCacheWrapper(pipelineScript)
        return this
    }

    CacheWrapper getCacheWrapper() {
        return cacheWrapper
    }

    @Override
    String toString() {
        return "PipelineConfigurer{" +
                "cacheWrapper=" + cacheWrapper.toString() +
                '}';
    }
}
