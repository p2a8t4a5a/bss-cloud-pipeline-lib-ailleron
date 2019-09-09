package org.p4.ci.pipeline.util

//@CompileStatic    - not fully supported in Jenkins Pipeline: "Line -1, expecting casting to com.cloudbees.groovy.cps.impl.CpsFunction but operand stack is empty"
class CacheWrapper {

    private final Script pipelineScript
    private final List<CacheProvider> cacheProviders

    CacheWrapper(Script pipelineScript, List<CacheProvider> cacheProviders) {
        this.pipelineScript = pipelineScript
        this.cacheProviders = cacheProviders
    }

    def runWithBuildCacheContext(Closure codeBlock) {
        prepareCacheSafely()

        def result = executeInScriptContext(codeBlock)

        summarizeCacheSafely()

        return result
    }

    private void prepareCacheSafely() {
        executeCacheOperationSafely("cache preparation",
                {CacheProvider cacheProvider -> cacheProvider.prepareCache()})
    }

    private void executeCacheOperationSafely(String operationName, Closure operationExecution) {
        executeInScriptContext {
            try {
                for (CacheProvider cacheProvider : cacheProviders) {
                    echo "Performing safe ${operationName} with ${cacheProvider.toString()}"
                    operationExecution(cacheProvider)
                }
            } catch (Exception e) {
                echo "Exception during ${operationName}. Marking pipeline as unstable. Message: ${e.message}"
                currentBuild.result = "UNSTABLE"
                echo "Exception: ${PipelineScriptUtils.fetchStackTraceAsString(e)}"
            }
        }
    }

    private void summarizeCacheSafely() {
        executeCacheOperationSafely("cache summarization",
                {CacheProvider cacheProvider -> cacheProvider.summarizeCache()})
    }

    private def executeInScriptContext(Closure stageBlock) {
        return PipelineScriptUtils.executeInScriptContext(pipelineScript, stageBlock)
    }

    //Unfortunately @ToString is not supported in Jenkins Pipeline - https://issues.jenkins-ci.org/browse/JENKINS-45901
    @Override
    String toString() {
        //TODO: cacheProviders.size() return 2, but cacheProviders.toString() and cacheProviders.foreach {} see only first element...
        //      Report bug in Pipeline CPS
        List<String> providerNames = []
        for (CacheProvider provider : cacheProviders) {
            providerNames.add(provider.toString())
        }
        return "CacheWrapper{" +
                "cacheProviders=" + providerNames +
                '}';
    }

    static CacheWrapper createStandardCacheWrapper(Script pipelineScript) {
        //TODO: Add npm cache once available
        return new CacheWrapper(pipelineScript, [new ReportingCacheProvider(pipelineScript), new MavenCacheProvider(pipelineScript),
                                                 new GradleCacheProvider(pipelineScript)])
    }

    static CacheWrapper createNoOpCacheWrapper(Script pipelineScript) {
        return new CacheWrapper(pipelineScript, [new DisabledCacheProvider(pipelineScript)])
    }
}
