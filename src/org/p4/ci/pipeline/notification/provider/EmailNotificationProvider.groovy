package org.p4.ci.pipeline.notification.provider

import org.p4.ci.pipeline.notification.NotificationProvider
import org.p4.ci.pipeline.util.PipelineScriptUtils

import static org.p4.ci.pipeline.util.PipelineScriptUtils.executeInScriptContext

class EmailNotificationProvider implements NotificationProvider {

    private final Script pipelineScript

    EmailNotificationProvider(Script pipelineScript) {
        this.pipelineScript = pipelineScript
    }

    @Override
    void notifyAboutBuildFailure(String jobName, String jobUrl, String emails) {
        String body = """
            Build ${jobName} failed.<br/><br/>
            More details: <a href="${jobUrl}">${jobUrl}</a>
        """.stripIndent()
        notifyAbout("[Jenkins] Build failure - $jobName", body, emails)
    }

    //TODO: Extract common logic to super class adding another provider
    private void notifyAbout(String subject, String body, String emails) {
        executeInScriptContext(pipelineScript) {
            if (env.EMAIL_NOFICATIONS_DISABLED == "true") {
                echo "Email notifications disabled with EMAIL_NOFICATIONS_DISABLED"
                return
            }

            if (!emails) {
                echo "No emails provided. Skipping notification."
                return
            }

            echo "Email address to send: ${emails}"

            try {
                //TODO: Consider switching to ext-mail
                mail body: body,
                        subject: subject,
                        from: 'ci@playmobile.pl',
                        mimeType: 'text/html',
                        to: emails

            } catch (Exception e) {
                currentBuild.result = "UNSTABLE"
                echo "Error while sending email: ${e.toString()}"
                echo "Exception: ${PipelineScriptUtils.fetchStackTraceAsString(e)}"
            }
        }
    }
}
