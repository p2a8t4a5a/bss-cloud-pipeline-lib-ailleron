package org.p4.ci.pipeline.util

/**
 * Set of utils related to pipeline logic
 */
class PipelineUtils {

    static boolean isJavaApplicationType(String appType) {
        return appType?.startsWith("springboot") || appType?.startsWith("java")
    }

    static boolean isGradleApplicationType(String appType) {
        return appType?.toLowerCase()?.contains("gradle")
    }
}
