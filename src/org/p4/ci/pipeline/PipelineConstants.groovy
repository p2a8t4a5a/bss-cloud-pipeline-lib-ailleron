package org.p4.ci.pipeline

interface PipelineConstants {

    public static final Map DEFAULT_VERIFY_DEPLOYMENT_TIMEOUT_MAP = [time: 10, unit: 'MINUTES']
    public static final String ARTIFACTORY_BASE_PATH_WITH_TRAILING_SLASH = "http://10.10.99.86:8081/artifactory/"

    public static final String DEFAULT_JAVA_TOOL_OPTIONS = "-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -Dsun.zip.disableMemoryMapping=true"
    public static final String DEFAULT_JAVA_11_TOOL_OPTIONS = "-XX:+UnlockExperimentalVMOptions" //cannot be empty to override inherited properties...

    public static final boolean PIPELINE_PREVIEW_MODE = true
    public static final boolean PIPELINE_RELEASE_MODE = false
}
