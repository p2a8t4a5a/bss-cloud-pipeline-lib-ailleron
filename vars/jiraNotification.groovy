def call(String status) {
    def comment = '<a href=' + env.BUILD_URL + '>Build ' + currentBuild.number + '</a> ' + env.BUILD_TIME
    def body = '{"releaseKey":"' + env.RELEASE_KEY + '","projectKey":"' + env.JIRA_PROJECT_KEY + '","versionName":"' + env.APP_NAME + '-' + env.APP_VERSION + '","componentName":"' + env.APP_NAME + '","status":"' + status + '","comment":"' + comment + '"}'
    print body
    try {
        httpRequest url: env.JIRA_API_URL, authentication: "${env.SYS_NAME}-cicd-jira-credentials", contentType: 'APPLICATION_JSON', httpMode: 'POST', requestBody: body
    } catch (e) {

    }
}
