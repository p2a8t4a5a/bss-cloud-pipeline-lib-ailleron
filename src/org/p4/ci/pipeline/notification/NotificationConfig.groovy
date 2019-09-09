package org.p4.ci.pipeline.notification

class NotificationConfig {

    //TODO: Emails would be moved out once more providers are available
    String emails
    boolean includeGitLabUser = true

    List<NotificationProvider> providers = []
}
