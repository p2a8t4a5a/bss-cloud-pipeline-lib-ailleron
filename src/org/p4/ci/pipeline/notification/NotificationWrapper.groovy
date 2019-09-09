package org.p4.ci.pipeline.notification

import hudson.model.Result
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.jenkinsci.plugins.workflow.support.steps.input.Rejection
import org.p4.ci.pipeline.notification.provider.EmailNotificationProvider

import static org.p4.ci.pipeline.util.PipelineScriptUtils.executeInScriptContext

class NotificationWrapper {

    private final Script pipelineScript
    private final NotificationConfig notificationConfig

    NotificationWrapper(Script pipelineScript, NotificationConfig notificationConfig) {
        this.notificationConfig = notificationConfig
        this.pipelineScript = pipelineScript
    }

    static NotificationWrapper emailNotificationWrapper(Script pipelineScript, String emails) {
        NotificationConfig notificationConfig = new NotificationConfig()
        notificationConfig.providers = [new EmailNotificationProvider(pipelineScript)]
        notificationConfig.emails = emails

        notificationWrapper(pipelineScript, notificationConfig)
    }

    static NotificationWrapper notificationWrapper(Script pipelineScript, NotificationConfig notificationConfig) {
        return new NotificationWrapper(pipelineScript, notificationConfig)
    }

    def withNotification(Closure pipelineCodeToWrap) {
        executeInScriptContext(pipelineScript) {
            String effectiveEmails = generateEffectiveEmails()
            try {
                pipelineCodeToWrap()

                if (currentBuild.result && Result.fromString(currentBuild.result).isWorseOrEqualTo(Result.FAILURE)) {
                    notifyAboutBuildFailure(env.JOB_NAME, env.BUILD_URL, effectiveEmails)
                }

            } catch (FlowInterruptedException e) {
                echo "Exception caught: ${e}. Causes: ${e.getCauses()}. Result: ${e.result}}"
                if (e.getCauses().any { it instanceof Rejection }) {    //aborted by user on approval, no email
                    echo "Ignoring ABORTED - build cancelled by user on approval"
                    currentBuild.result = "ABORTED"
                } else if (isBuildSuperseded(e)) {
                    echo "Ignoring NOT_BUILD - build superseded by new build"
                    currentBuild.result = "NOT_BUILD"
                } else {
                    notifyAboutBuildFailure(env.JOB_NAME, env.BUILD_URL, effectiveEmails)
                }

                throw e

            } catch (AssertionError | Exception e) {

                echo "Exception caught: ${e}. ${e.metaClass.hasProperty(e, "result") ? "Result: " + e.result : ""}"
                notifyAboutBuildFailure(env.JOB_NAME, env.BUILD_URL, effectiveEmails)

                throw e
            }
        }
    }

    private String generateEffectiveEmails() {
        return executeInScriptContext(pipelineScript) {
            String additionalEmails = notificationConfig.includeGitLabUser && env.gitlabUserEmail ? ",${env.gitlabUserEmail}" : ""
            echo "Effective emails: '${notificationConfig.emails}', + '${additionalEmails}'"
            return notificationConfig.emails + additionalEmails //Possible duplication seems to be handled by the plugin
        }
    }

    private boolean isBuildSuperseded(FlowInterruptedException thrown) {
        //toString() could be used to do not introduce dependency on jenkins-core
        return thrown.result == Result.NOT_BUILT
    }

    private void notifyAboutBuildFailure(String jobName, String jobUrl, String emails) {
        for (NotificationProvider provider : notificationConfig.providers) {
            provider.notifyAboutBuildFailure(jobName, jobUrl, emails)
        }
    }
}
