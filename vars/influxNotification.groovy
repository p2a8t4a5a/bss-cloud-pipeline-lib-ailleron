def call(String stage) {
    def now = new Date().getTime()
    def body = 'events,appName=' + env.APP_NAME + ',project=' + stage + ' title="Deployed v' + env.PIPELINE_VERSION + '",text="Deployment",tags="deploy" ' + now
    print body
    try {
        // ITPBC-535
        httpRequest url: "http://10.10.33.51:12100/write?db=${env.INFLUX_NOTIFICATION_DB}&precision=ms", httpMode: 'POST', contentType: 'APPLICATION_FORM', requestBody: body
    } catch (e) {
        println "Warning. Influx call failed: ${e}"
    }
}