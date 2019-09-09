void tagAplicationWithProjectTagInSonar(String appName, String tagName) {
    scriptUtils.mayFail {

        echo "Setting '$tagName' in SonarQube for application '$appName'"

        Properties props = readProperties file: 'target/sonar/report-task.txt'
        String projectKey = props.get("projectKey")
        echo "Read projectKey '$projectKey'"
        assert projectKey

        //TODO: Credentials could be used, but token is passed as username which is not masked. To be revisited after Sonar plugin handles credentials itself
        String basicAuthToken = "${env.SONAR_AUTH_TOKEN}:"
        String response = httpRequest consoleLogResponseBody: true,
                customHeaders: [[maskValue: true, name: 'Authorization', value: "Basic ${basicAuthToken.bytes.encodeBase64().toString()}"]],
                httpMode: 'POST',
                responseHandle: 'NONE',
                timeout: 30,
                url: "${env.SONAR_HOST_URL}/api/project_tags/set?project=${projectKey}&tags=${tagName}"

        echo "Response: $response"
    }
}
