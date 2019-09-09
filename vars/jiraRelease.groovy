def call() {
    try {
        def response = httpRequest url: "${env.JIRA_API_URL}?projectKey=${env.JIRA_PROJECT_KEY}&versionName=${env.APP_NAME}-${env.APP_VERSION}", authentication: "${env.SYS_NAME}-cicd-jira-credentials"
        print response.content
        def jsonObj = utils.parseJson(response.content)
        env.RELEASE_KEY = jsonObj[0]?.releaseKey
        print env.RELEASE_KEY
        env.PROD_DATE = jsonObj[0]?.prodDate
    } catch(e) {
        println "Warning. Jira call failed: ${e}"
    }

    def releaseKey = env.RELEASE_KEY ? "Release <a href=\"https://jira.playmobile.pl/jira/projects/RM/issues/${env.RELEASE_KEY}\">${env.RELEASE_KEY}</a>" : "Release empty"
    currentBuild.description = releaseKey + " : ${env.PIPELINE_VERSION}"
}
