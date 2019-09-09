package org.p4.ci.pipeline.logic

import static org.p4.ci.pipeline.util.PipelineScriptUtils.executeInScriptContext

/**
 * Runs passed code in a context of the passed JDK.
 *
 * The JAVA_HOME, JAVA_TOOL_OPTIONS and PATH are set to required values for the execution and restored to the original state.
 *
 * Important. As a side effect all changes made to the aforementioned variables in the passed code are reset as well.
 */
class JdkChangeWrapper implements BlockWrapper {

    private final String jdkName
    private final String jdkToolOpts

    JdkChangeWrapper(String jdkName, String jdkToolOpts) {
        this.jdkName = jdkName
        this.jdkToolOpts = jdkToolOpts
    }

    @Override
    def wrap(Script pipelineScript, Closure codeBlock) {
        return executeInScriptContext(pipelineScript) {
            String oldJavaHome = env.JAVA_HOME
            String oldJavaToolOptions = env.JAVA_TOOL_OPTIONS
            String oldPath = env.PATH

            try {
                jdk = tool name: jdkName, type: 'jdk'
                env.JAVA_HOME = jdk
                env.JAVA_TOOL_OPTIONS = jdkToolOpts
                env.PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
                echo "Running code block with JDK: ${jdkName}, JAVA_HOME: ${jdk} and JAVA_TOOL_OPTIONS: ${jdkToolOpts}"
                return codeBlock()
            } finally {
                env.PATH = oldPath
                env.JAVA_HOME = oldJavaHome
                env.JAVA_TOOL_OPTIONS = oldJavaToolOptions
                echo "Restoring original JDK settings. JAVA_HOME: ${oldJavaHome} and JAVA_TOOL_OPTIONS: ${oldJavaToolOptions}"
            }
        }
    }
}
