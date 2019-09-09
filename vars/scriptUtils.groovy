import static org.p4.ci.pipeline.util.PipelineScriptUtils.fetchStackTraceAsString

def mayFail(boolean makeUnstable = true, Closure codeBlock) {
    try {
        codeBlock()
    } catch (Exception | NoSuchMethodError e) { //to handle also: java.lang.NoSuchMethodError: No such DSL method 'withSonarQubeEnv' found among steps [...]
        echo "Ignoring exception while processing code block: ${e}"
        //TODO: Only if debug is enabled?
        echo "Exception: ${fetchStackTraceAsString(e)}"
        if (makeUnstable) {
            currentBuild.result = "UNSTABLE"
        }
    }
}

void unsupported() {
    flexibleUnsupported()
}

void unsupportedJustWarnAndMarkUnstable() {
    flexibleUnsupported(false)
}

private void flexibleUnsupported(boolean failBuild = true) {
    String message = "Requested operation is not supported."
    if (failBuild) {
        error(message)
    } else {
        echo "WARN. ${message}"
        currentBuild.result = "UNSTABLE"
    }
}
