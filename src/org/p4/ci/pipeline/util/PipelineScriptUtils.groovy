package org.p4.ci.pipeline.util

/**
 * Set of technical utils related to pipeline script
 */
class PipelineScriptUtils {

    static String fetchStackTraceAsString(Throwable e) {
        //It would be easier with StackWalking from Java 9
        StringWriter sw = new StringWriter()
        e.printStackTrace(new PrintWriter(sw))
        return sw.toString()
    }

    //Workaround, as in declarative pipeline environment has to be a string or a funciton call (not a variable...)
    static String returnPassedValue(String valueToBeReturned) {
        return valueToBeReturned
    }

    static def executeInScriptContext(Script pipelineScript, Closure stageBlock) {
        //pipelineScript.with { stageBlock.call() } doesn't seem to work on Jenkins
        stageBlock.delegate = pipelineScript
        return stageBlock.call()
    }

}
