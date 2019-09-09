package org.p4.ci.pipeline.notification

interface NotificationProvider {

    void notifyAboutBuildFailure(String jobName, String jobUrl, String emails)
}